package example.tiny.di.sample;

import javax.inject.Named;

@Named
public class Foo {
    public String getMessage() {
        return "Hello!";
    }
}
