import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final int THREADS = 64;
    private final Map<String, Map<String, Handler>> allHandlers;
    final ExecutorService threadPool;

    public Server() {
        this.threadPool = Executors.newFixedThreadPool(THREADS);
        this.allHandlers = new ConcurrentHashMap<>();
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
        try (final BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())){
            final Request request = Request.parse(socket);
            if (!allHandlers.containsKey(request.getMethod())) {
                notFound(out);
                return;
            }
            if (!allHandlers.get(request.getMethod()).containsKey(request.getPath())) {
                notFound(out);
                return;
            }
            allHandlers.get(request.getMethod()).get(request.getPath()).handle(request, out);
        } catch (IOException | URISyntaxException ex) {
            ex.printStackTrace();
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        if (!allHandlers.containsKey(method)) {
            allHandlers.put(method, new ConcurrentHashMap<>());
        }
        allHandlers.get(method).put(path, handler);
    }

    private void notFound(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Not Found\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }
}