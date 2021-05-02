package example.tiny.di.context;

// クラス名の後ろに総称型を書き、インスタンス生成時に型を指定する
public class LocalCache<T> {
    private ThreadLocal<T> local = new InheritableThreadLocal<>();
    private String name;

    public LocalCache(String name) {
        this.name = name;
    }

    public T get() {
        T obj = local.get();

        if (obj != null) {
            return obj;
        }

        obj = (T) Context.getBean(name);
        local.set(obj);

        return obj;
    }
}
