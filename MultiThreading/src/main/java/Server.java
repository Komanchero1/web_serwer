
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
    // Создается мапа, где будут храниться значения в виде ключ - метод запроса GET или POST,
    // значение - другая мапа, где ключ - это путь запроса /messages, значение - обработчик запроса Handler
    private static final  Map<String, Map<String, Handler>> handlers = new HashMap<>();
    // Создается список путей к файлам
    private static final List<String> validPaths = List.of("/shrek.png", "/shrek_1.png", "/shrek_2.png",
            "/shrek_3.png", "/shrek_4.png", "/classic.html");
    // Создается маяк для синхронизации доступа к handlers
    private static final ReentrantLock handlersLock = new ReentrantLock();

    // Метод добавляет обработчик запросов в handlers в качестве аргументов используется метод запроса,
    // путь запроса, обработчик запроса
    public void addHandler(String method, String path, Handler handler) {
        handlersLock.lock(); // Блокируем доступ к handlers
        try {
            // Проверяется, существует ли вложенная мапа для указанного запроса, если нет, добавляется
            handlers.computeIfAbsent(method, k -> new HashMap<>())
                    // Добавляется обработчик запросов во вложенную карту для указанного пути
                    .put(path, handler);
        } finally {
            handlersLock.unlock(); // Разблокируем доступ к handlers
        }
    }

    // Метод запускает сервер, который прослушивает входящие соединения на указанном порту.
    // Входящие запросы обрабатываются в пуле потоков.
    public static void listen(int port) {
        try (final var serverSocket = new ServerSocket(port)) { // Создается сокет
            // Создается пул из 64 потоков
            final var threadPool = Executors.newFixedThreadPool(64);
            while (true) {
                final var socket = serverSocket.accept();// Прослушиваются соединения
                // Создается новый поток из пула потоков и передается ему лямбда-функция,
                // которая вызывает метод handleClient в качестве аргумента socket
                threadPool.execute(() -> handleClient(socket));
            }

        } catch (IOException e) { // Перехватываем любые ошибки ввода-вывода
            e.printStackTrace();
        }
    }

    // Метод для обработки входящих запросов от клиента в качестве аргумента передается socket
    private static void handleClient(Socket socket) {
        // Создаются BufferedReader и BufferedOutputStream для чтения и записи данных в сокет
        try (final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             final var out = new BufferedOutputStream(socket.getOutputStream())) {

            Request request = RequestParser.parseRequest(in);// Считывается запрос и парсится
            // Получается обработчик запросов для указанного метода и пути запроса из поля `handlers`
            Handler handler = getHandler(request.getMethod(), request.getPath());
            if (handler != null ) {//если обработчик не null
                handler.handle(request, out);//производится обработка запроса
            } else {
                sendNotFound(out); // Если обработчик не найден, отправляем ошибку 404
            }
            // Если обработчик найден
            if (!isStaticFile(request.getPath())) {
                // Вызывается для обработки запроса
                handler.handle(request, out);
            } else {
                // Если нет, вызывается метод sendStaticFile для обработки статического файла
                sendStaticFile(request.getPath(), out);
            }
        } catch (IOException e) { // Перехватываем любые ошибки ввода-вывода
            e.printStackTrace();
        } finally {
            try {
                socket.close(); // Закрывается сокет после обработки запроса
            } catch (IOException e) { // Перехватываем любые ошибки ввода-вывода
                e.printStackTrace();
            }
        }
    }


    // Метод для получения обработчика запросов для указанного метода и пути запроса из поля handlers
    // Если обработчик запросов не найден, возвращает null
    private static Handler getHandler(String method, String path) {
        // Получаем вложенную мапу для указанного метода запроса из handlers, если вложеной мапа
        // не существует, она создается и добавляется в поле handlers
        return handlers.getOrDefault(method, new HashMap<>())
                .get(path);// Получаем обработчик запросов для указанного пути
    }

    // Метод проверяет, является ли указанный путь к файлу статическим файлом
    private static boolean isStaticFile(String path) {
        return validPaths.contains(path);
    }

    // Метод отправляет статический файл клиенту
    private static void sendStaticFile(String path, BufferedOutputStream out) throws IOException {
        // Создается путь к статическому файлу
        Path filePath = Path.of(".", "public", path);
        if (Files.exists(filePath)) { // Если файл существует по указанному пути
            String mimeType = Files.probeContentType(filePath);// Определяется тип файла
            long fileLength = Files.size(filePath);// Определяется размер файла в байтах
            out.write(( // Формируется HTTP заголовок ответа
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" + // Используем определенный MIME тип
                            "Content-Length: " + fileLength + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());// Преобразовывается сообщение в массив байтов
            Files.copy(filePath, out);// Копируется содержимое файла в выходной поток
            out.flush();// Отправляется клиенту
        } else {
            // Если файл не существует, отправляется HTTP ответ клиенту с кодом ошибки 404
            sendNotFound(out);
        }
    }

    //  Метод отправляет клиенту ответ с кодом состояния 404 (Not Found),
    //  указывая, что запрошенный ресурс не найден.
    private static void sendNotFound(BufferedOutputStream out) throws IOException {
        out.write(( // Записывается сообщение ниже в выходной поток
                "HTTP/1.1 404 Not Found\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());// Преобразовываем сообщение в массив байтов
        out.flush();// Очистка буфера обмена
    }

    // Метод считывает тело запроса и возвращает его в виде массива байтов
    static byte[] readBody(BufferedReader in) throws IOException {
        // Создается объект StringBuilder для хранения тела запроса
        StringBuilder bodyBuilder = new StringBuilder();
        String line; // Объявляем переменную типа String для хранения каждой строки тела запроса
        // Объявляет переменную типа boolean и инициализирует ее значением `false`
        // эта переменная используется для определения начала тела запроса
        boolean bodyStarted = false;
        // Цикл будет работать пока не будет достигнут конец тела запроса
        try {
            while ((line = in.readLine()) != null) {
                // Проверяется, является ли текущая строка пустой и не началось ли еще тело запроса
                if (line.isEmpty() && !bodyStarted) {
                    // Если тело запроса началось присваиваем переменной bodyStarted значение true
                    bodyStarted = true;
                } else if (bodyStarted) { // Если тело запроса началось
                    // Добавляет текущую строку в bodyBuilder и добавляет символ новой строки
                    bodyBuilder.append(line).append("\n");
                }
            }
        } finally {
            in.close(); // Закрываем BufferedReader
        }
        return bodyBuilder.toString().getBytes();// Возвращает тело запроса как массив байтов
    }

    public static void main(String[] args) {
        final var server = new Server(); // Создается экземпляр класса Server
        // Добавляет обработчик запросов для метода GET и пути /messages
        server.addHandler("GET", "/messages", (request, responseStream) -> {
            // Обработчик запросов отправляет ответ "GET /messages response" клиенту
            Server.sendResponse(responseStream, "GET /messages response");

        });
        // Добавляет обработчик запросов для метода POST и пути /messages
        server.addHandler("POST", "/messages", (request, responseStream) -> {
            // Обработчик запросов отправляет ответ "POST /messages response" клиенту
            Server.sendResponse(responseStream, "POST /messages response");
        });
        server.listen(8080);// Запускается сервер на порту 8080
    }

    // Отправляетс ответ клиенту, записывая заголовок HTTP-ответа и содержимое ответа в выходной поток
    private static void sendResponse(BufferedOutputStream out, String content) throws IOException {
        out.write(( // Записывается сообщение ниже в выходной поток
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/plain\r\n" +
                        "Content-Length: " + content.length() + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());// Преобразовываем сообщение в массив байтов
        out.flush();// Очистка буфера обмена
    }

    // Функциональный интерфейс, который определяет метод для обработки запросов
    // и отправки ответов клиентам
    @FunctionalInterface
    public interface Handler {
        // Метод должен реализовывать логику обработки запроса и отправки ответа клиенту
        // Request - запрос клиента, BufferedOutputStream - буферизованный выходной поток
        // для отправки ответа клиенту
        void handle(Request request, BufferedOutputStream responseStream) throws IOException;
    }
}
