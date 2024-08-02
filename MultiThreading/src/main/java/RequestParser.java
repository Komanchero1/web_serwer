import java.io.BufferedReader;
import java.io.IOException;


public class RequestParser {
    //метод парсит запрос клиента из in и возвращает объект `Request
    public static Request parseRequest(BufferedReader in) throws IOException {
        //считывается строка и сохраняется в переменную requestLine
        final String requestLine = in.readLine();
        if (requestLine == null) { //проверка что строка не пустая
            //если да выбрасывается сообщение об ошибке
          //  throw new IOException("Invalid request line");
         }
        //считывается строка и разбивается по пробелам
        final var parts = requestLine.split(" ");

        if (parts.length != 3) {//проверяется что строка запроса содержит 3 части
            throw new IOException("Invalid request line"); //если нет выбрасывается сообщение об ошибке
        }

        final var method = parts[0];//сохраняем в переменные 1 часть строки- метод запроса
        final var path = parts[1];// 2 часть строки - путь запроса
        //у объекта Server вызываем метод readBody передаем ему в параметры BufferedReader in
        // и получаем из потока in тело метода в виде массива байтов
        final var body = Server.readBody(in);
        //возвращается объект Request с методом, путем и телом запроса
        return new Request(method, path, body);
    }
}
