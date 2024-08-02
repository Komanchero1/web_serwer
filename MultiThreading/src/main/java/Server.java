
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class Server {
    // ��������� ����, ��� ����� ��������� �������� � ���� ���� - ����� ������� GET ��� POST,
    // �������� - ������ ����, ��� ���� - ��� ���� ������� /messages, �������� - ���������� ������� Handler
    private static final  Map<String, Map<String, Handler>> handlers = new HashMap<>();
    // ��������� ������ ����� � ������
    private static final List<String> validPaths = List.of("/shrek.png", "/shrek_1.png", "/shrek_2.png",
            "/shrek_3.png", "/shrek_4.png", "/classic.html");
    // ��������� ���� ��� ������������� ������� � handlers
    private static final ReentrantLock handlersLock = new ReentrantLock();

    // ����� ��������� ���������� �������� � handlers � �������� ���������� ������������ ����� �������,
    // ���� �������, ���������� �������
    public void addHandler(String method, String path, Handler handler) {
        handlersLock.lock(); // ��������� ������ � handlers
        try {
            // �����������, ���������� �� ��������� ���� ��� ���������� �������, ���� ���, �����������
            handlers.computeIfAbsent(method, k -> new HashMap<>())
                    // ����������� ���������� �������� �� ��������� ����� ��� ���������� ����
                    .put(path, handler);
        } finally {
            handlersLock.unlock(); // ������������ ������ � handlers
        }
    }

    // ����� ��������� ������, ������� ������������ �������� ���������� �� ��������� �����.
    // �������� ������� �������������� � ���� �������.
    public static void listen(int port) {
        try (final var serverSocket = new ServerSocket(port)) { // ��������� �����
            // ��������� ��� �� 64 �������
            final var threadPool = Executors.newFixedThreadPool(64);
            while (true) {
                final var socket = serverSocket.accept();// �������������� ����������
                // ��������� ����� ����� �� ���� ������� � ���������� ��� ������-�������,
                // ������� �������� ����� handleClient � �������� ��������� socket
                threadPool.execute(() -> handleClient(socket));
            }

        } catch (IOException e) { // ������������� ����� ������ �����-������
            e.printStackTrace();
        }
    }

    // ����� ��� ��������� �������� �������� �� ������� � �������� ��������� ���������� socket
    private static void handleClient(Socket socket) {
        // ��������� BufferedReader � BufferedOutputStream ��� ������ � ������ ������ � �����
        try (final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             final var out = new BufferedOutputStream(socket.getOutputStream())) {

            Request request = RequestParser.parseRequest(in);// ����������� ������ � ��������
            // ���������� ���������� �������� ��� ���������� ������ � ���� ������� �� ���� `handlers`
            Handler handler = getHandler(request.getMethod(), request.getPath());
            if (handler != null ) {//���� ���������� �� null
                handler.handle(request, out);//������������ ��������� �������
            } else {
                sendNotFound(out); // ���� ���������� �� ������, ���������� ������ 404
            }
            // ���� ���������� ������
            if (!isStaticFile(request.getPath())) {
                // ���������� ��� ��������� �������
                handler.handle(request, out);
            } else {
                // ���� ���, ���������� ����� sendStaticFile ��� ��������� ������������ �����
                sendStaticFile(request.getPath(), out);
            }
        } catch (IOException e) { // ������������� ����� ������ �����-������
            e.printStackTrace();
        } finally {
            try {
                socket.close(); // ����������� ����� ����� ��������� �������
            } catch (IOException e) { // ������������� ����� ������ �����-������
                e.printStackTrace();
            }
        }
    }


    // ����� ��� ��������� ����������� �������� ��� ���������� ������ � ���� ������� �� ���� handlers
    // ���� ���������� �������� �� ������, ���������� null
    private static Handler getHandler(String method, String path) {
        // �������� ��������� ���� ��� ���������� ������ ������� �� handlers, ���� �������� ����
        // �� ����������, ��� ��������� � ����������� � ���� handlers
        return handlers.getOrDefault(method, new HashMap<>())
                .get(path);// �������� ���������� �������� ��� ���������� ����
    }

    // ����� ���������, �������� �� ��������� ���� � ����� ����������� ������
    private static boolean isStaticFile(String path) {
        return validPaths.contains(path);
    }

    // ����� ���������� ����������� ���� �������
    private static void sendStaticFile(String path, BufferedOutputStream out) throws IOException {
        // ��������� ���� � ������������ �����
        Path filePath = Path.of(".", "public", path);
        if (Files.exists(filePath)) { // ���� ���� ���������� �� ���������� ����
            String mimeType = Files.probeContentType(filePath);// ������������ ��� �����
            long fileLength = Files.size(filePath);// ������������ ������ ����� � ������
            out.write(( // ����������� HTTP ��������� ������
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" + // ���������� ������������ MIME ���
                            "Content-Length: " + fileLength + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());// ����������������� ��������� � ������ ������
            Files.copy(filePath, out);// ���������� ���������� ����� � �������� �����
            out.flush();// ������������ �������
        } else {
            // ���� ���� �� ����������, ������������ HTTP ����� ������� � ����� ������ 404
            sendNotFound(out);
        }
    }

    //  ����� ���������� ������� ����� � ����� ��������� 404 (Not Found),
    //  ��������, ��� ����������� ������ �� ������.
    private static void sendNotFound(BufferedOutputStream out) throws IOException {
        out.write(( // ������������ ��������� ���� � �������� �����
                "HTTP/1.1 404 Not Found\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());// ��������������� ��������� � ������ ������
        out.flush();// ������� ������ ������
    }

    // ����� ��������� ���� ������� � ���������� ��� � ���� ������� ������
    static byte[] readBody(BufferedReader in) throws IOException {
        // ��������� ������ StringBuilder ��� �������� ���� �������
        StringBuilder bodyBuilder = new StringBuilder();
        String line; // ��������� ���������� ���� String ��� �������� ������ ������ ���� �������
        // ��������� ���������� ���� boolean � �������������� �� ��������� `false`
        // ��� ���������� ������������ ��� ����������� ������ ���� �������
        boolean bodyStarted = false;
        // ���� ����� �������� ���� �� ����� ��������� ����� ���� �������
        try {
            while ((line = in.readLine()) != null) {
                // �����������, �������� �� ������� ������ ������ � �� �������� �� ��� ���� �������
                if (line.isEmpty() && !bodyStarted) {
                    // ���� ���� ������� �������� ����������� ���������� bodyStarted �������� true
                    bodyStarted = true;
                } else if (bodyStarted) { // ���� ���� ������� ��������
                    // ��������� ������� ������ � bodyBuilder � ��������� ������ ����� ������
                    bodyBuilder.append(line).append("\n");
                }
            }
        } finally {
            in.close(); // ��������� BufferedReader
        }
        return bodyBuilder.toString().getBytes();// ���������� ���� ������� ��� ������ ������
    }

    public static void main(String[] args) {
        final var server = new Server(); // ��������� ��������� ������ Server
        // ��������� ���������� �������� ��� ������ GET � ���� /messages
        server.addHandler("GET", "/messages", (request, responseStream) -> {
            // ���������� �������� ���������� ����� "GET /messages response" �������
            Server.sendResponse(responseStream, "GET /messages response");

        });
        // ��������� ���������� �������� ��� ������ POST � ���� /messages
        server.addHandler("POST", "/messages", (request, responseStream) -> {
            // ���������� �������� ���������� ����� "POST /messages response" �������
            Server.sendResponse(responseStream, "POST /messages response");
        });
        server.listen(8080);// ����������� ������ �� ����� 8080
    }

    // ����������� ����� �������, ��������� ��������� HTTP-������ � ���������� ������ � �������� �����
    private static void sendResponse(BufferedOutputStream out, String content) throws IOException {
        out.write(( // ������������ ��������� ���� � �������� �����
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/plain\r\n" +
                        "Content-Length: " + content.length() + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());// ��������������� ��������� � ������ ������
        out.flush();// ������� ������ ������
    }

    // �������������� ���������, ������� ���������� ����� ��� ��������� ��������
    // � �������� ������� ��������
    @FunctionalInterface
    public interface Handler {
        // ����� ������ ������������� ������ ��������� ������� � �������� ������ �������
        // Request - ������ �������, BufferedOutputStream - �������������� �������� �����
        // ��� �������� ������ �������
        void handle(Request request, BufferedOutputStream responseStream) throws IOException;
    }
}
