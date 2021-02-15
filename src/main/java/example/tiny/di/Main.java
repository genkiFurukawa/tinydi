package example.tiny.di;

import example.tiny.di.context.Context;
import example.tiny.di.sample.Bar;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) {
        // Contextに型を登録
        Context.autoRegister();

        // beanを取得
        Bar bar = (Bar) Context.getBean("bar");
        bar.showMessage();

        ExecutorService es = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 2; ++i) {
            es.execute(() -> {
                bar.longProcess();
            });
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
            }
        }
        es.shutdown();
    }
}
