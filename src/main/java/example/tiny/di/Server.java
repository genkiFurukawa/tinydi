package example.tiny.di;

import example.tiny.di.annotation.Path;
import example.tiny.di.context.Context;
import lombok.AllArgsConstructor;

import java.io.*;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        Context.registerdClasses().forEach(entry -> {
            Class cls = entry.getValue();
            Path rootAnnotation = (Path) cls.getAnnotation(Path.class);

            if (rootAnnotation == null) {
                return;
            }

            String root = trimSlash(rootAnnotation.value());
            for (Method method: cls.getMethods()) {
                Path pathAnnotation = method.getAnnotation(Path.class);

                if (pathAnnotation == null) {
                    continue;
                }

                String path = root + "/" + pathAnnotation.value();
                methods.put(path, new ProcessorMethod(entry.getKey(), method));
            }

        });

        Pattern pattern = Pattern.compile("([A-Z]+) ([^ ]+) (.+)");

        ServerSocket serverSocket = new ServerSocket(8989);

        while (true) {
            Socket socket = serverSocket.accept();

            new Thread(() -> {
                try (InputStream is = socket.getInputStream();
                     BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is))
                ) {
                    // HTTPリクエストの１行目「GET /index HTTP/1.1」とかになっているので抜き出す
                    String first = bufferedReader.readLine();
                    System.out.println("#first:" + first);
                    Matcher matcher = pattern.matcher(first);
                    matcher.find();
                    String httpMethod = matcher.group(1);
                    String path = matcher.group(2);
                    String protocol = matcher.group(3);

                    System.out.println("httpMethod:" + httpMethod + ", path:" + path + ", protocol:" + protocol);

                    for (String line; (line = bufferedReader.readLine()) != null && !line.isEmpty(); ) {
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

                            try{
                                Object bean = Context.getBean(method.name);
                                Object output = method.method.invoke(bean);

                                pw.println("HTTP/1.0 200 OK");
                                pw.println("Content-Type: text/html");
                                pw.println();
                                pw.println(output);
                            } catch (Exception ex) {
                                pw.println("HTTP/1.0 200 OK");
                                pw.println("Content-Type: text/html");
                                pw.println();
                                pw.println("<h1>500 Internal Server Error</h1>");
                                pw.println(ex);
                            }
                        }
                    }

                } catch (IOException e) {
                    System.out.println(e);
                }
            }).start();
        }
    }
}
