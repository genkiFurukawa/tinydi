package example.tiny.di;

import example.tiny.di.context.Context;
import example.tiny.di.sample.Bar;

public class Main {
    public static void main(String[] args) {
        // Contextに型を登録
        Context.autoRegister();

        // beanを取得
        Bar bar = (Bar) Context.getBean("bar");
        bar.showMessage();
    }
}
