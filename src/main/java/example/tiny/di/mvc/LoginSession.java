package example.tiny.di.mvc;

import example.tiny.di.annotation.SessionScoped;
import lombok.Data;

import javax.inject.Named;
import java.time.LocalDateTime;

@Named
@SessionScoped
@Data
public class LoginSession {
    boolean logined;
    LocalDateTime loginTime;
}
