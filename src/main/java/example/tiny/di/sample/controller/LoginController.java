package example.tiny.di.sample.controller;

import example.tiny.di.annotation.Path;
import example.tiny.di.mvc.LoginSession;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.LocalDateTime;

@Named
@Path("login")
public class LoginController {
    @Inject
    LoginSession loginSession;

    @Path("index")
    public String index() {
        System.out.println(">> LoginController.index()");
        String title = "<h1>Login</h1>";
        System.out.println("loginSession:" + loginSession);
        if (loginSession.isLogined()) {
            System.out.println("<< LoginController.index()");
            return title + "Login at " + loginSession.getLoginTime();
        } else {
            System.out.println("<< LoginController.index()");
            return title + "Not Login";
        }
    }

    @Path("login")
    public String login() {
        System.out.println(">> LoginController.login()");
        loginSession.setLogined(true);
        loginSession.setLoginTime(LocalDateTime.now());
        System.out.println("loginSession:" + loginSession);
        System.out.println("<< LoginController.login()");
        return "<h1>Login</h1>login";
    }

    @Path("logout")
    public String logout() {
        loginSession.setLogined(false);
        return "<h1>Login</h1>logout";
    }
}
