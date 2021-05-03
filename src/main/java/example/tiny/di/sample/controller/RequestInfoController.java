package example.tiny.di.sample.controller;

import example.tiny.di.annotation.Path;
import example.tiny.di.mvc.RequestInfo;

import javax.inject.Inject;
import javax.inject.Named;

@Named
@Path("info")
public class RequestInfoController {
    @Inject
    RequestInfo requestInfo;

    @Path("index")
    public String index() {
        System.out.println(">> RequestInfoController.index()");

        return String.format("<h1>Info</h1>Host:%s<br/>Path:%s<br/>UserAgent:%s<br/>",
                requestInfo.getInetAddress(), requestInfo.getPath(), requestInfo.getUserAgent());

    }
}
