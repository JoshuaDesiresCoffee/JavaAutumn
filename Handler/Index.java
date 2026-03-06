package Handler;

import Service.templating.Templater;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Index {

    public static void get(HttpExchange exchange) throws IOException {
        String html = Templater.render("index.html", Map.of(
                "title", "This is the Java Autumn framework!",
                "subtitle", "Templater is now replacing placeholders."
        ));
        byte[] payload = html.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (var os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }
}
