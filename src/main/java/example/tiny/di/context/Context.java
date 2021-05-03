package example.tiny.di.context;

import example.tiny.di.Main;
import example.tiny.di.annotation.InvokeLog;
import example.tiny.di.annotation.RequestScoped;
import example.tiny.di.annotation.SessionScoped;
import example.tiny.di.mvc.BeanSession;
import javassist.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Context = 前後関係、文脈、脈絡、コンテキスト、状況、環境
public class Context {
    static Map<String, Class> types = new HashMap<>();
    static Map<String, Object> beans = new HashMap<>();
    static ThreadLocal<Map<String, Object>> requestBeans = new InheritableThreadLocal<>();
    static BeanSession beanSession;


    public static void setBeanSession(BeanSession beanSession) {
        Context.beanSession = beanSession;
    }

    /**
     * @Namedの付いたクラスをtypesに登録する
     */
    public static void autoRegister() {

        try {
            URL res = Main.class.getResource("/" + Main.class.getName().replace('.', '/') + ".class");

            String packageName = Main.class.getPackageName();

            // Mainクラスからの相対パスとパッケージ名からクラス名を取得して登録する
            Path classPath = new File(new File(res.toURI()).getParent()).toPath();
            Files.walk(classPath)
                    .filter(p -> !Files.isDirectory(p)) // ファイル以外は除外
                    .filter(p -> p.toString().endsWith(".class")) // .class以外は除外
                    .map(p -> classPath.relativize(p))// Mainクラスからの相対パスを取得
                    .map(p -> p.toString().replace(File.separatorChar, '.')) // 相対パスは/となっているので、.に変換
                    .map(p -> packageName + "." + p) // パッケージ名とくっつける
                    .map(n -> n.substring(0, n.length() - 6)) // .classを文字列から除外
                    .forEach(n -> {
                        try {
                            Class c = Class.forName(n);

                            // @Namedのアノテーションが付いていたら登録
                            if (c.isAnnotationPresent(Named.class)) {
                                String simpleName = c.getSimpleName();
                                register(simpleName.substring(0, 1).toLowerCase() + simpleName.substring(1), c);
                            }
                        } catch (ClassNotFoundException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void register(String name, Class type) {
        types.put(name, type);
    }

    /**
     * nameと一致するオブジェクトをbeans or requestBeansを取得する
     *
     * @param name name
     * @return Object
     */
    public static Object getBean(String name) {
        System.out.println(">> Context.getBean(String " + name + ")");

        Class type = types.get(name);
        Objects.requireNonNull(type, name + " not found.");

        // @RequestScopedが付いているときは、scopeをrequestBeansにする
        Map<String, Object> scope;
        if (type.isAnnotationPresent(RequestScoped.class)) {
            System.out.println("RequestScoped.class");

            scope = requestBeans.get();
            if (scope == null) {
                System.out.println("scope is null.");
                scope = new HashMap<>();
                requestBeans.set(scope);
            }
        } else if (type.isAnnotationPresent(SessionScoped.class)) {
            System.out.println("SessionScoped.class");
            scope = beanSession.getBeans();
        }else {
            System.out.println("else");
            scope = beans;
        }

        // 検索してあればそのオブジェクトを返す
        for (Map.Entry<String, Object> bean : scope.entrySet()) {
            System.out.println("bean.getKey():" + bean.getKey());
            if (bean.getKey().equals(name)) {
                System.out.println("あるので再利用");
                System.out.println("<< Context.getBean(String " + name + ")");
                return bean.getValue();
            }
        }

        // ない場合は作成して返す
        try {
            System.out.println("ないので生成");
            System.out.println("<< Context.getBean(String " + name + ")");
            Object object = createObject(type);
            scope.put(name, object);

            return object;
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new RuntimeException(name + " can not instanciate", ex);
        }
    }

    // https://docs.oracle.com/javase/jp/1.4/api/java/lang/InstantiationException.html
    // InstantiationException =>
    // アプリケーションが Class クラスの newInstance メソッドを使ってクラスのインスタンスを生成しようとしたときに、
    // クラスがインタフェースまたは abstract クラスであるために指定されたオブジェクトのインスタンスを生成できない場合にスローされます。
    // memo: メソッドの戻り値の型の手前に<T>を書くと、その型パラメータTをメソッド内で使用することができる。
    private static <T> T createObject(Class<T> type) throws InstantiationException, IllegalAccessException {
        T object;
        // InvokeLogアノテーションがある時はクラスをラップしたクラスにする
        if (Stream.of(type.getDeclaredMethods()).anyMatch(m -> m.isAnnotationPresent(InvokeLog.class))) {
            object = wrap(type).newInstance();
        } else {
            object = type.newInstance();
        }

        inject(type, object);

        return object;
    }

    /**
     * オブジェクトを生成する。
     *
     * @param type type
     * @param <T>  T
     * @return T
     * @InvokeLogが付いているときはメソッドを拡張したオブジェクトを返す
     */
    private static <T> Class<? extends T> wrap(Class<T> type) {
        try {
            // ClassPoolという、クラスパスを管理しクラスファイルをディスク等から実際に読み込む作業を行う、
            // Javassistの要になるオブジェクトを取得
            ClassPool pool = ClassPool.getDefault();

            // 暫定対処（ないとNotFoundExceptionが吐かれる）
            URL res = Main.class.getResource("/" + Main.class.getName().replace('.', '/') + ".class");
            String packageName = Main.class.getPackageName();
            Path mainClassPath = new File(new File(res.toURI()).getParent()).toPath();
            Files.walk(mainClassPath)
                    .filter(p -> !Files.isDirectory(p)) // ファイル以外は除外
                    .filter(p -> p.toString().endsWith(".class")) // .class以外は除外
                    .map(p -> mainClassPath.relativize(p))// Mainクラスからの相対パスを取得
                    .map(p -> p.toString().replace(File.separatorChar, '.')) // 相対パスは/となっているので、.に変換
                    .map(p -> packageName + "." + p) // パッケージ名とくっつける
                    .map(n -> n.substring(0, n.length() - 6)) // .classを文字列から除外
                    .forEach(n -> {
                        try {
                            Class c = Class.forName(n);
                            pool.insertClassPath(new ClassClassPath(c));

                        } catch (ClassNotFoundException ex) {
                            throw new RuntimeException(ex);
                        }
                    });


            // クラス名$$を生成し、Superclassを元のクラスにする
            CtClass orgCls = pool.get(type.getName());
            CtClass cls = pool.makeClass(type.getName() + "$$");
            cls.setSuperclass(orgCls);

            // @InvokeLogが付いていたらメソッドに時刻を表示する処理を加える
            for (CtMethod method : orgCls.getDeclaredMethods()) {
                if (!method.hasAnnotation(InvokeLog.class)) {
                    continue;
                }

                CtMethod newMethod = new CtMethod(
                        method.getReturnType(), method.getName(), method.getParameterTypes(), cls);
                newMethod.setExceptionTypes(method.getExceptionTypes());
                newMethod.setBody(
                        "{"
                                + "  System.out.println(java.time.LocalDateTime.now() + "
                                + "\":" + method.getName() + " invoked.\"); "
                                + "  return super." + method.getName() + "($$);"
                                + "}");
                cls.addMethod(newMethod);
            }
            return cls.toClass();
        } catch (NotFoundException | CannotCompileException | IOException | URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static int scopeRank(Class type) {
        if (type.isAnnotationPresent(RequestScoped.class)) {
            return 0;
        }

        if (type.isAnnotationPresent(SessionScoped.class)) {
            return 5;
        }

        return 10;
    }

    /**
     * @Injectの付いたフィールドに値をセットする
     *
     * @param type type
     * @param object object
     * @param <T> <T>
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     */
    private static <T> void inject(Class<T> type, T object) throws IllegalArgumentException, IllegalAccessException {
        System.out.println(">> Context.inject()");
        System.out.println("type:" + type + ", object:" + object);

        for (Field field : type.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Inject.class)) {
                continue;
            }
            // フィールドに値をセットする
            field.setAccessible(true);

            Object bean;
            System.out.println("type:" + field.getType() + ", name:" + field.getName());
            System.out.println("scopeRank(type):" + scopeRank(type) + ", scopeRank(field.getType()):" + scopeRank(field.getType()));
            if (scopeRank(type) > scopeRank(field.getType())) {
                bean = getBean(field.getName());

                if (bean == null) {
                    System.out.println("bean is null. field.getType() is " + field.getType() + ". field.getName() is " + field.getName());
                    bean = scopeWrapper(field.getType(), field.getName());
                }
            } else {
                bean = getBean(field.getName());
            }

            System.out.println("bean:" + bean);
            System.out.println("Hash Code:" + object.hashCode());
            // フィールドに値をセットする
            field.set(object, bean);
        }

        System.out.println("<< Context.inject()");
    }

    private static Set<String> cannotOverrides = Stream.of("finalize", "clone").collect(Collectors.toSet());

    /**
     *
     *
     * @param type type
     * @param name name
     * @param <T>  T
     * @return T
     */
    private static <T> T scopeWrapper(Class<T> type, String name) {
        try {
            System.out.println(">> scopeWrapper");
            ClassPool pool = ClassPool.getDefault();

            // 暫定対処（ないとNotFoundExceptionが吐かれる）
            URL res = Main.class.getResource("/" + Main.class.getName().replace('.', '/') + ".class");
            String packageName = Main.class.getPackageName();
            Path mainClassPath = new File(new File(res.toURI()).getParent()).toPath();
            Files.walk(mainClassPath)
                    .filter(p -> !Files.isDirectory(p)) // ファイル以外は除外
                    .filter(p -> p.toString().endsWith(".class")) // .class以外は除外
                    .map(p -> mainClassPath.relativize(p))// Mainクラスからの相対パスを取得
                    .map(p -> p.toString().replace(File.separatorChar, '.')) // 相対パスは/となっているので、.に変換
                    .map(p -> packageName + "." + p) // パッケージ名とくっつける
                    .map(n -> n.substring(0, n.length() - 6)) // .classを文字列から除外
                    .forEach(n -> {
                        try {
                            Class c = Class.forName(n);
                            pool.insertClassPath(new ClassClassPath(c));

                        } catch (ClassNotFoundException ex) {
                            throw new RuntimeException(ex);
                        }
                    });

            CtClass cls = pool.getOrNull(type.getName() + "$$_");

            // Nowというクラスにはもともといくつかメソッドがある
            // ここでLocalCache<Now> orgというフィールドを作成し、orgのメソッドを元のメソッドに上書きする
            // 各スレッドごとに保持される値に基づいたメソッドを作成できる
            if (cls == null) {
                // $$_をつけるクラスを作成する
                CtClass orgCls = pool.get(type.getName());
                cls = pool.makeClass(type.getName() + "$$_");
                cls.setSuperclass(orgCls);

                // ローカルオブジェクトを保持するフィールドを用意
                CtClass tl = pool.get(LocalCache.class.getName());

                // 引数にフィールドの型、フィールド名、宣言先を指定
                CtField org = new CtField(tl, "org", cls);
                // orgというフィールドにnew example.tiny.di.context.LocalCache("now");をセットする
                // example.tiny.di.context.LocalCacheはnameというフィールドをもつ
                cls.addField(org, "new " + LocalCache.class.getName() + "(\"" + name + "\");");

                for (CtMethod method : orgCls.getMethods()) {
                    if (Modifier.isFinal(method.getModifiers()) | cannotOverrides.contains(method.getName())) {
                        continue;
                    }

                    CtMethod override = new CtMethod(method.getReturnType(), method.getName(), method.getParameterTypes(), cls);
                    override.setExceptionTypes(method.getExceptionTypes());
                    // 各メソッドに対応する委譲メソッドを作る
                    // ex:{  return ((example.tiny.di.sample.Now)org.get()).toString($$);}
                    override.setBody("{" + "  return ((" + type.getName() + ")org.get())." + method.getName() + "($$);" + "}");
                    cls.addMethod(override);
                }
            }
            return (T) cls.toClass().getDeclaredConstructor().newInstance();
        } catch (IOException | URISyntaxException | NotFoundException | IllegalAccessException | CannotCompileException | InstantiationException | NoSuchMethodException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Collection<Map.Entry<String, Class>> registerdClasses() {
        return types.entrySet();
    }
}
