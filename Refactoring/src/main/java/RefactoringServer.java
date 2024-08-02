import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;

public class RefactoringServer {

    public static void main(String[] args) {
        //��������� ������������ ������ �� ����� � ������
        final var validPaths = List.of("/shrek.png", "/shrek_1.png", "/shrek_2.png",
                "/shrek_3.png", "/shrek_4.png", "/classic.html");
        //��������� ����� ��� ������������� ����������� � ����� 8080
        try (final var serverSocket = new ServerSocket(8080)) {
            //��������� ��� � 64 �������� �������� ��� ����������� ��������� �������� ��������
            final var threadPool = Executors.newFixedThreadPool(64);
            while (true) {
                final var socket = serverSocket.accept();//������� ����������� �������
                //��������� ������ ��������� ������� ��������� ��������� run
                //��� ������ ����� ���������� � ���� �������
                threadPool.execute(() -> {
                    try (
                            //�������������� ������� ����� � ������������ ��� � BufferedReader
                            //����� ������� ��������� ��������
                            final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            //���� ��������� ����� , ����� ���������� ������
                            final var out = new BufferedOutputStream(socket.getOutputStream());
                    ) {


                        final var requestLine = in.readLine(); //���������� �������
                        final var parts = requestLine.split(" ");//��������� ������ �� ��������

                        if (parts.length != 3) {// ���� ����� ������ ����� �������� �� ����� 3
                            // just close socket//��������� ����������
                            return;
                        }

                        //��������� ������ ������� ������ � ���������� path
                        // � ������� ������ ��������� ��� �����
                        final var path = parts[1];
                        //���� � ������ validPaths ��� ������ ���� ���������� ��������� �� ������
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
                        //��������� ������ Path �������������� ������ ���� � ����� "."- ������� �������
                        //"public"- ������������� ���� � �����������, path- ��� �����
                        final var filePath = Path.of(".", "public", path);
                        final var mimeType = Files.probeContentType(filePath);//���������� ��� �����

                        // ���� ��� ����� ������������ /classic.html
                        if (path.equals("/classic.html")) {
                            final var template = Files.readString(filePath);//��������� ���������� �����
                            final var content = template.replace(//��������
                                    "{time}",//��� ������
                                    LocalDateTime.now().toString()//�� ������� ����� � ����
                            ).getBytes();
                            out.write((
                                    "HTTP/1.1 200 OK\r\n" +
                                            "Content-Type: " + mimeType + "\r\n" +
                                            "Content-Length: " + content.length + "\r\n" +
                                            "Connection: close\r\n" +
                                            "\r\n"
                            ).getBytes());//��������������� � ������ ������
                            out.write(content);//�������� ��������� � �������� �����
                            out.flush();//�������� ���������
                            return;
                        }

                        final var length = Files.size(filePath);//������������ ������ �����
                        out.write(( //���������� ������ � �������� �����
                                "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: " + mimeType + "\r\n" +
                                        "Content-Length: " + length + "\r\n" +
                                        "Connection: close\r\n" +
                                        "\r\n"
                        ).getBytes());//����������������� ��������� � ������ ������
                        Files.copy(filePath, out);//���������� ���������� ����� filePath � �������� ����� out
                        out.flush();//����������� ����� ������
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}