package example.tiny.di.context;

import example.tiny.di.Main;
import example.tiny.di.annotation.InvokeLog;
import example.tiny.di.sample.Bar;
import javassist.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

// Context = 前後関係、文脈、脈絡、コンテキスト、状況、環境
public class Context {
    static Map<String, Class> types = new HashMap<>();
    static Map<String, Object> beans = new HashMap<>();


    public static void autoRegister() {
        try {
            URL res = Main.class.getResource(
                    "/" + Main.class.getName().replace('.', '/') + ".class");

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

    public static Object getBean(String name) {
        // 検索してあればそのオブジェクトを返す
        for (Map.Entry<String, Object> bean : beans.entrySet()) {
            if (bean.getKey().equals(name)) {
                return bean.getValue();
            }
        }
        // ない場合は作成して返す
        Class type = types.get(name);
        Objects.requireNonNull(type, name + " not found.");
        try {
            return createObject(type);
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

    private static <T> void inject(Class<T> type, T object) throws IllegalArgumentException, IllegalAccessException {
        for (Field field : type.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Inject.class)) {
                continue;
            }
            // フィールドに値をセットする
            field.setAccessible(true);
            // フィールドに値をセットする
            field.set(object, getBean(field.getName()));
        }
    }
}
