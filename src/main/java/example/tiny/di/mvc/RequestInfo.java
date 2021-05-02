package example.tiny.di.mvc;

import example.tiny.di.annotation.RequestScoped;
import lombok.Data;

import javax.inject.Named;
import java.net.InetAddress;

@Named
@RequestScoped
@Data
public class RequestInfo {
    private String path;
    private InetAddress inetAddress;
    private String userAgent;
    private String sessionId;
}
