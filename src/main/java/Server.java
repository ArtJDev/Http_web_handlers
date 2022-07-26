import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final String GET = "GET";
    private final String POST = "POST";
    private final int THREADS = 64;
    private final List<String> allowedMethods = List.of("GET", "POST");
    private final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
    private final Map<String, Map<String, Handler>> allHandlers;
    private final Map<String, Handler> subHandlers;
    final ExecutorService threadPool;

    public Server() {
        this.threadPool = Executors.newFixedThreadPool(THREADS);
        this.allHandlers = new ConcurrentHashMap<>();
        this.subHandlers = new ConcurrentHashMap<>();
    }

    public void listen(int port) {
        try (final ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                final Socket socket = serverSocket.accept();
                threadPool.submit(() -> requestProcess(socket));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void requestProcess(Socket socket) {
        try {
            final BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
            final Request request = parse(socket);
            if (request.getMethod().equals(GET) && validPaths.contains(request.getPath())) {
                allHandlers.get(GET).get(request.getPath()).handle(request, out);
            }
            if (request.getMethod().equals(POST) && validPaths.contains(request.getPath())) {
                allHandlers.get(POST).get(request.getPath()).handle(request, out);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        subHandlers.put(path, handler);
        allHandlers.put(method, subHandlers);
    }

    public Request parse(Socket socket) throws IOException {
            final var in = new BufferedInputStream(socket.getInputStream());
            final var out = new BufferedOutputStream(socket.getOutputStream());
            final var limit = 4096;

            in.mark(limit);
            final var buffer = new byte[limit];
            final var read = in.read(buffer);
            // ищем request line
            final var requestLineDelimiter = new byte[]{'\r', '\n'};
            final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            if (requestLineEnd == -1) {
                badRequest(out);
                socket.close();
            }
            // читаем request line
            final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (requestLine.length != 3) {
                badRequest(out);
                socket.close();
            }
            final var method = requestLine[0];
            if (!allowedMethods.contains(method)) {
                badRequest(out);
                socket.close();
            }
            System.out.println(method);
            final var path = requestLine[1];
            if (!path.startsWith("/")) {
                badRequest(out);
                socket.close();
            }
            System.out.println(path);
            final var protocol = requestLine[2];
            // ищем заголовки
            final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            final var headersStart = requestLineEnd + requestLineDelimiter.length;
            final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            if (headersEnd == -1) {
                badRequest(out);
                socket.close();
            }
            // отматываем на начало буфера
            in.reset();
            // пропускаем requestLine
            in.skip(headersStart);

            final var headersBytes = in.readNBytes(headersEnd - headersStart);
            final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
            System.out.println(headers);

            // для GET тела нет
            final int length;
            final byte[] bodyBytes;
            String body = "";
            if (!method.equals("GET")) {
                in.skip(headersDelimiter.length);
                // вычитываем Content-Length, чтобы прочитать body
                final var contentLength = extractHeader(headers, "Content-Length");
                if (contentLength.isPresent()) {
                    length = Integer.parseInt(contentLength.get());
                    bodyBytes = in.readNBytes(length);
                    body = new String(bodyBytes);
                    System.out.println(body);
                }
            }
        return new Request(method, path, protocol, headers, body);
    }

    private Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    // from google guava with modifications
    private int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}