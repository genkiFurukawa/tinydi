package example.tiny.di;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;

public class Server {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8989);

        while (true) {
            Socket socket = serverSocket.accept();

            new Thread(() -> {
                try (InputStream is = socket.getInputStream();
                     BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is))
                ) {
                    String firstLine = bufferedReader.readLine();
                    for (String line; (line = bufferedReader.readLine()) != null && !line.isEmpty(); ) {
                        System.out.println("#" + line);

                        try (OutputStream os = socket.getOutputStream();
                             PrintWriter pw = new PrintWriter(os)) {
                            pw.println("HTTP/1.0 200 OK");
                            pw.println("Content-Type: text/html");
                            pw.println();
                            pw.println("<h1>Hello!</h1>");
                            pw.println(LocalDateTime.now());
                        }
                    }

                } catch (IOException e) {
                    System.out.println(e);
                }
            }).start();
        }
    }
}
