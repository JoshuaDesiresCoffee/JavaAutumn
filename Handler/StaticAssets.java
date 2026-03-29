package Handler;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class StaticAssets {

    public static void style(HttpExchange exchange) throws IOException {
        serveFile(exchange, Path.of("public", "style.css"), "text/css; charset=UTF-8");
    }

    private static void serveFile(HttpExchange exchange, Path filePath, String contentType) throws IOException {
        if (!Files.exists(filePath)) {
            send(exchange, 404, "text/plain; charset=UTF-8", "Not found");
            return;
        }

        byte[] payload = Files.readString(filePath).getBytes(StandardCharsets.UTF_8);
        send(exchange, 200, contentType, payload);
    }

    private static void send(HttpExchange exchange, int statusCode, String contentType, String body) throws IOException {
        send(exchange, statusCode, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int statusCode, String contentType, byte[] payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }
}
