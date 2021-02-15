package example.tiny.di.sample;

import example.tiny.di.annotation.InvokeLog;

import javax.inject.Named;

@Named
public class Foo {
    public String getMessage() {
        return "Hello!";
    }

    @InvokeLog
    public String getName() {
        return "foo!";
    }
}
