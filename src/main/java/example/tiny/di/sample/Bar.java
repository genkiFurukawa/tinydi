package example.tiny.di.sample;

import example.tiny.di.annotation.InvokeLog;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.LocalDateTime;

@Named
public class Bar {
    @Inject
    Foo foo;

    @Inject
    Now now;

    @InvokeLog
    public void showMessage() {
        System.out.println(foo.getMessage() + " " + foo.getName());
    }

    // 5秒待ってログを出力する
    public void longProcess() {
        now.setTime(LocalDateTime.now());
        System.out.println("start:" + now.getTime());
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ex) {
        }
        System.out.println("end  :" + now.getTime());
    }
}
