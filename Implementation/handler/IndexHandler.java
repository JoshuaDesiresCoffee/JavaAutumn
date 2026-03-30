package Implementation.handler;

import Autumn.handler.Exchange;
import Autumn.templating.Templater;

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
}
