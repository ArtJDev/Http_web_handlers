import java.util.List;

public class Request {
    private final String method;
    private final String path;
    private final String protocol;
    private final List<String> headers;
    private final String body;

    public Request(String method, String path, String protocol, List<String> headers, String body) {
        this.method = method;
        this.path = path;
        this.protocol = protocol;
        this.headers = headers;
        this.body = body;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }
}