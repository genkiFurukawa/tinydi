package example.tiny.di.context;

import example.tiny.di.Main;

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
                    .map(p -> packageName  + "." + p) // パッケージ名とくっつける
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
    private static <T> T createObject(Class<T> type) throws InstantiationException, IllegalAccessException {
        T object = type.newInstance();

        // getDeclaredFields => クラスのフィールドを取得
        for (Field field : type.getDeclaredFields()) {
            // getAnnotation => アノテーションの取得
            Inject inject = field.getAnnotation(Inject.class);
            if (inject == null) {
                continue;
            }
            // フィールドを操作可能にする
            field.setAccessible(true);
            // フィールドに値をセットする
            field.set(object, getBean(field.getName()));
        }

        return object;
    }
}
