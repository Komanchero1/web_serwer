public class Request {
    private final String method;//��� �������� ������ �������
    private final String path;//��� �������� ���� �������
    private final byte[] body;//��� �������� ���� �������

    //����������� � ���� ���������� ������
    public Request(String method, String path, byte[] body) {
        this.method = method;
        this.path = path;
        this.body = body;
    }

    //���������� ����� �������
    public String getMethod() {
        return method;
    }

    //���������� ���� �������
    public String getPath() {
        return path;
    }

    //���������� ���� �������
    public byte[] getBody() {
        return body;
    }
}