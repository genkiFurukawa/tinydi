package example.tiny.di.sample;

import example.tiny.di.annotation.InvokeLog;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class Bar {
    @Inject
    Foo foo;

    @InvokeLog
    public void showMessage() {
        System.out.println(foo.getMessage() + " " + foo.getName());
    }
}
