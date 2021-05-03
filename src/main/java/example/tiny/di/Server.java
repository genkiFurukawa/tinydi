package example.tiny.di;

import example.tiny.di.annotation.Path;
import example.tiny.di.context.Context;
import example.tiny.di.mvc.BeanSession;
import example.tiny.di.mvc.RequestInfo;
import lombok.AllArgsConstructor;

import java.io.*;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Server {

    @AllArgsConstructor
    static class ProcessorMethod {
        String name;
        Method method;
    }

    static String trimSlash(String str) {
        return str.replaceFirst("^/", "").replaceFirst("/$", "");
    }

    public static void main(String[] args) throws IOException {
        Map<String, ProcessorMethod> methods = new HashMap<>();

        Context.autoRegister();
        BeanSession beanSession = new BeanSession();
        Context.setBeanSession(beanSession);

        Context.registerdClasses().forEach(entry -> {
            Class cls = entry.getValue();
            Path rootAnnotation = (Path) cls.getAnnotation(Path.class);

            if (rootAnnotation == null) {
                return;
            }

            String root = trimSlash(rootAnnotation.value());

            if (!root.isEmpty()) {
                root = "/" + root;
            }

            for (Method method : cls.getMethods()) {
                Path pathAnnotation = method.getAnnotation(Path.class);

                if (pathAnnotation == null) {
                    continue;
                }

                String path = root + "/" + pathAnnotation.value();
                methods.put(path, new ProcessorMethod(entry.getKey(), method));
            }

        });

        Pattern pattern = Pattern.compile("([A-Z]+) ([^ ]+) (.+)");
        Pattern patternHeader = Pattern.compile("([A-Za-z-]+): (.+)");

        AtomicLong lastSessionId = new AtomicLong(10);

        ServerSocket serverSocket = new ServerSocket(8989);


        while (true) {
            Socket socket = serverSocket.accept();

            new Thread(() -> {
                try (InputStream is = socket.getInputStream();
                     BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is))
                ) {
                    System.out.println("[0] start");
                    // HTTPリクエストの１行目「GET /index HTTP/1.1」とかになっているので抜き出す
                    String first = bufferedReader.readLine();
                    Matcher matcher = pattern.matcher(first);
                    matcher.find();
                    String httpMethod = matcher.group(1);
                    String path = matcher.group(2);
                    String protocol = matcher.group(3);

//                    System.out.println("httpMethod:" + httpMethod + ", path:" + path + ", protocol:" + protocol);
//                    System.out.println("socket.getLocalAddress():" + socket.getLocalAddress());

                    RequestInfo info = (RequestInfo) Context.getBean("requestInfo");

                    info.setInetAddress(socket.getLocalAddress());
                    info.setPath(path);

                    Map<String, String> cookies = new HashMap<>();
                    for (String line; (line = bufferedReader.readLine()) != null && !line.isEmpty(); ) {
                        Matcher matcherHeader = patternHeader.matcher(line);

                        if (matcherHeader.find()) {
                            String value = matcherHeader.group(2);
                            switch (matcherHeader.group(1)) {
                                case "User-Agent":
                                    info.setUserAgent(matcherHeader.group(2));
                                    break;
                                case "Cookie":
                                    Stream.of(value.split(";"))
                                            .map(exp -> exp.trim().split("="))
                                            .filter(kv -> kv.length == 2)
                                            .forEach(kv -> cookies.put(kv[0], kv[1]));
                            }
                        }
                    }

                    String sessionId = cookies.get("jsessionid");

                    if (sessionId != null) {
                        if (!beanSession.isSessionRegistered(sessionId)) {
                            sessionId = null;
                        }
                    }

                    if (sessionId == null) {
                        sessionId = Long.toString(lastSessionId.incrementAndGet());
                    }

                    beanSession.setSessionId(sessionId);

                    info.setSessionId(sessionId);

                    try (OutputStream os = socket.getOutputStream();
                         PrintWriter pw = new PrintWriter(os)) {

                        ProcessorMethod method = methods.get(path);

                        if (method == null) {
                            pw.println("HTTP/1.0 404 Not Found");
                            pw.println("Content-Type: text/html");
                            pw.println();
                            pw.println("<h1>404 Not Found</h1>");
                            pw.println(path + " Not Found");
                            return;
                        }

                        try {
                            System.out.println("[1] info: " + info);
                            System.out.println("[1] HashCode:" + info.hashCode());

                            Object bean = Context.getBean(method.name);
                            System.out.println("[2] method.name: " + method.name + ", method.method:" + method.method);

                            // invoke:呼び出す
                            Object output = method.method.invoke(bean);

                            pw.println("HTTP/1.0 200 OK");
                            pw.println("Content-Type: text/html");
                            pw.println("Set-Cookie: jsessionid=" + sessionId + "; path=/");
                            pw.println();
                            pw.println(output);
                        } catch (Exception ex) {
                            ex.printStackTrace();

                            pw.println("HTTP/1.0 500 Internal Server Error");
                            pw.println("Content-Type: text/html");
                            pw.println();
                            pw.println("<h1>500 Internal Server Error</h1>");
                            pw.println(ex);
                        }
                    }
                } catch (IOException e) {
                    System.out.println(e);
                }
            }).start();
        }
    }
}
