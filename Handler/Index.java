package Handler;

import Service.Templater;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Index {

    public static void get(HttpExchange exchange) throws IOException, IOException {

        // get user from repository
        // give render data to templater
        String html = Templater.render("index.html", null);
        byte[] payload = html.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, payload.length);
        try (var os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }
}
