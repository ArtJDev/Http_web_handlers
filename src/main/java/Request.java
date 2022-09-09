import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Request {
    private static final List<String> allowedMethods = List.of("GET", "POST");

    private final String method;
    private final String path;
    private final String protocol;
    private final List<String> headers;
    private final String body;
    private static List<NameValuePair> queryParams = null;

    public Request(String method, String path, String protocol, List<String> headers, String body, List<NameValuePair> queryParams) {
        this.method = method;
        this.path = path;
        this.protocol = protocol;
        this.headers = headers;
        this.body = body;
        this.queryParams = queryParams;
    }

    public List<NameValuePair> getQueryParams() {
        return queryParams;
    }

    public List<NameValuePair> getQueryParam(String name) {
        List<NameValuePair> targetQueryParams = null;
        for (NameValuePair queryParam : queryParams) {
            if (queryParam.getName().equals(name)) {
                targetQueryParams.add(queryParam);
            }
        }
        return targetQueryParams;
    }

    public static Request parse(Socket socket) throws IOException, URISyntaxException {
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
        final var pathLine = requestLine[1];
        if (!pathLine.startsWith("/")) {
            badRequest(out);
            socket.close();
        }
        URIBuilder builder = new URIBuilder(pathLine);
        String path = builder.getPath();
        System.out.println(path);

        queryParams = builder.getQueryParams();
        for (NameValuePair queryParam : queryParams) {
            System.out.println(queryParam);
        }
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
        return new Request(method, path, protocol, headers, body, queryParams);
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }
    // from google guava with modifications
    private static int indexOf(byte[] array, byte[] target, int start, int max) {
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


    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }
}