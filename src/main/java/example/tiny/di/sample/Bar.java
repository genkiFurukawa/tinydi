package example.tiny.di.sample;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class Bar {
    @Inject
    Foo foo;

    public void showMessage() {
        System.out.println(foo.getMessage());
    }
}
