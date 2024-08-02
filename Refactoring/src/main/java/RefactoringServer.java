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
        //создается неизменяемый список из путей к файлам
        final var validPaths = List.of("/shrek.png", "/shrek_1.png", "/shrek_2.png",
                "/shrek_3.png", "/shrek_4.png", "/classic.html");
        //создается сокет для прослушивания подключений к порту 8080
        try (final var serverSocket = new ServerSocket(8080)) {
            //создается пул с 64 рабочими потоками для паралельной обработки запросов клиентов
            final var threadPool = Executors.newFixedThreadPool(64);
            while (true) {
                final var socket = serverSocket.accept();//ожидаем подключения клиента
                //создается лямбда выражение которое реалезует интерфейс run
                //эта задача будет выполнятся в пуле потоков
                threadPool.execute(() -> {
                    try (
                            //инициализируем входной поток и обворачиваем его в BufferedReader
                            //чтобы запросы считывать строками
                            final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            //тоже исходящий поток , чтобы отправлять строки
                            final var out = new BufferedOutputStream(socket.getOutputStream());
                    ) {


                        final var requestLine = in.readLine(); //считывание запроса
                        final var parts = requestLine.split(" ");//разбиваем строку по пробелам

                        if (parts.length != 3) {// если длина строки после разбивки не равна 3
                            // just close socket//закрываем соединение
                            return;
                        }

                        //сохраняем второй элемент строки в переменную path
                        // в котором должен находится имя файла
                        final var path = parts[1];
                        //если в списке validPaths нет такого пути отправляем сообщение об ошибке
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
                        //создается объект Path представляющий полный путь к файлу "."- текущий каталог
                        //"public"- относительный путь к подкаталогу, path- имя файла
                        final var filePath = Path.of(".", "public", path);
                        final var mimeType = Files.probeContentType(filePath);//определяем тип файла

                        // если имя файла сответствует /classic.html
                        if (path.equals("/classic.html")) {
                            final var template = Files.readString(filePath);//считываем содержимое файла
                            final var content = template.replace(//заменяем
                                    "{time}",//эту строку
                                    LocalDateTime.now().toString()//на текущее время и дату
                            ).getBytes();
                            out.write((
                                    "HTTP/1.1 200 OK\r\n" +
                                            "Content-Type: " + mimeType + "\r\n" +
                                            "Content-Length: " + content.length + "\r\n" +
                                            "Connection: close\r\n" +
                                            "\r\n"
                            ).getBytes());//преобразовываем в массив байтов
                            out.write(content);//отправка сообщения в выходной поток
                            out.flush();//отправка сообщения
                            return;
                        }

                        final var length = Files.size(filePath);//определяется размер файла
                        out.write(( //записываем данные в выходной поток
                                "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: " + mimeType + "\r\n" +
                                        "Content-Length: " + length + "\r\n" +
                                        "Connection: close\r\n" +
                                        "\r\n"
                        ).getBytes());//преобразовывается сообщение в массив байтов
                        Files.copy(filePath, out);//копируется содержимое файла filePath в выходной поток out
                        out.flush();//освобождаем буфер обмена
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