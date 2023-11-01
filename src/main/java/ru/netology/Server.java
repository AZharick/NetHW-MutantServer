package ru.netology;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
   private static final int PORT = 9999;
   private static final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html",
           "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
   ServerSocket serverSocket;
   Socket clientSocket;
   private Map<String, Handler> getHandlers;
   private Map<String, Handler> postHandlers;

   public Server() {
      getHandlers = new HashMap<>();
      postHandlers = new HashMap<>();
   }

   public void start() throws IOException {
      serverSocket = new ServerSocket(PORT);
      System.out.println(getDateAndTime() + "Server started at port "+ PORT);
      ExecutorService threadPool = Executors.newFixedThreadPool(64);

      while (true) {
         clientSocket = serverSocket.accept();
         threadPool.submit(() -> {
            try {
               handleRequest(clientSocket);
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         });
      }
   }

   private void handleRequest(Socket socket) throws IOException {
      while (true) {
         try (
                 final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 final var out = new BufferedOutputStream(socket.getOutputStream());
         ) {
            final var requestLine = in.readLine();  // reading only request line
            System.out.println("=========\n" + getDateAndTime() + "> requestLine: " + requestLine);
            final var parts = requestLine.split(" ");

            if (parts.length != 3) {
               return; //close socket
            }

            final var method = parts[0];
            System.out.println(getDateAndTime() + "> method: " + method);
            final var path = parts[1];
            System.out.println(getDateAndTime() + "> path: " + path);
            Request request = new Request(method,path);

            if (!validPaths.contains(path)) {
               out.write((
                       "HTTP/1.1 404 Not Found\r\n" +
                               "Content-Length: 0\r\n" +
                               "Connection: close\r\n" +
                               "\r\n"
               ).getBytes());
               out.flush();
               return;
            }

            final var filePath = Path.of(".", "public", path);
            final var mimeType = Files.probeContentType(filePath);

            // special case for classic
            if (path.equals("/classic.html")) {
               final var template = Files.readString(filePath);
               final var content = template.replace(
                       "{time}",
                       LocalDateTime.now().toString()
               ).getBytes();
               out.write((
                       "HTTP/1.1 200 OK\r\n" +
                               "Content-Type: " + mimeType + "\r\n" +
                               "Content-Length: " + content.length + "\r\n" +
                               "Connection: close\r\n" +
                               "\r\n"
               ).getBytes());
               out.write(content);
               out.flush();
               return;
            }

            final var length = Files.size(filePath);
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            Files.copy(filePath, out);
            out.flush();
         }//try-with-res
      }//while
   }//handleRequest

   public void addHandler(String method, String path, Handler handler) {
      if (method.equals("GET") && validPaths.contains(path)) {
         getHandlers.put(path, handler);
      }
   }

   private static String getDateAndTime() {
      String datePattern = "[HH:mm:ss] ";
      DateFormat d = new SimpleDateFormat(datePattern);
      Date today = Calendar.getInstance().getTime();
      String str = d.format(today);
      return str;
   }

}//Server