package example.tiny.di.sample.mvc;

import example.tiny.di.annotation.Path;

import javax.inject.Named;
import java.time.LocalDateTime;

@Named
@Path("")
public class IndexController {
    @Path("index")
    public String index() {
        return "<h1>Hello</h1>" + LocalDateTime.now();
    }

    @Path("message")
    public String message() {
        return "<h1>Message</h1>Nice to meet you!";
    }
}
