package Implementation.handler;

import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.ObjectToMapConverter;
import Autumn.templating.Templater;
import Implementation.repository.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


public class IndexHandler {

    public static void get(Exchange exchange) throws IOException {

        String html = Templater.render("index.html", null);
        byte[] payload = html.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, payload.length);
        try (var os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    public static void showUser(Exchange exchange) throws IOException {

        var usersList = Db.instance.SELECT.FROM(User.class).LIMIT(1).EXEC();
        var userMap = ObjectToMapConverter.convert(usersList.getFirst());

        String html = Templater.render("user.html", userMap);
        byte[] payload = html.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, payload.length);
        try (var os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }
}
