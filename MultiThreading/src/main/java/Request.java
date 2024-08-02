public class Request {
    private final String method;//для хранения метода запроса
    private final String path;//для хранения пути запроса
    private final byte[] body;//для хранения тела запроса

    //конструктор с выше указанными полями
    public Request(String method, String path, byte[] body) {
        this.method = method;
        this.path = path;
        this.body = body;
    }

    //возвращает метод запроса
    public String getMethod() {
        return method;
    }

    //возвращает путь запроса
    public String getPath() {
        return path;
    }

    //возвращает тело запроса
    public byte[] getBody() {
        return body;
    }
}