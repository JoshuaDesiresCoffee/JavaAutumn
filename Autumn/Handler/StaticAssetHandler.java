package Autumn.handler;

import Autumn.templating.Templater;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class StaticAssetHandler {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "css",  "text/css; charset=UTF-8",
            "js",   "application/javascript; charset=UTF-8",
            "png",  "image/png",
            "jpg",  "image/jpeg",
            "jpeg", "image/jpeg",
            "svg",  "image/svg+xml",
            "ico",  "image/x-icon"
    );

    private final String urlPrefix;
    private final Path fileRoot;

    public StaticAssetHandler(String urlPrefix, String fileRoot) {
        this.urlPrefix = urlPrefix;
        this.fileRoot = Path.of(fileRoot);
    }

    public static Handler withRoot(String urlPrefix, String fileRoot) {
        var handler = new StaticAssetHandler(urlPrefix, fileRoot);
        return handler::serve;
    }

    public void serve(Exchange exchange) throws IOException {
        String uriPath = exchange.uri().getPath();

        if (!uriPath.startsWith(urlPrefix)) {
            send(exchange, 404, "text/plain; charset=UTF-8", "Not found");
            return;
        }

        String relative = uriPath.substring(urlPrefix.length());

        if (relative.isBlank()) {
            send(exchange, 404, "text/plain; charset=UTF-8", "Not found");
            return;
        }

        String ext = getExtension(relative);
        if (ext == null || !CONTENT_TYPES.containsKey(ext)) {
            send(exchange, 404, "text/plain; charset=UTF-8", "Not found");
            return;
        }

        Path resolved = fileRoot.resolve(relative).normalize();
        if (!resolved.startsWith(fileRoot)) {
            send(exchange, 403, "text/plain; charset=UTF-8", "Forbidden");
            return;
        }

        String contentType = CONTENT_TYPES.get(ext);
        serveFile(exchange, resolved, contentType);
    }

    private static String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        if (dot == -1 || dot == path.length() - 1) {
            return null;
        }
        return path.substring(dot + 1).toLowerCase();
    }

    private static void serveFile(Exchange exchange, Path filePath, String contentType) throws IOException {
        if (Files.exists(filePath)) {
            byte[] payload = Files.readAllBytes(filePath);
            send(exchange, 200, contentType, payload);
            return;
        }

        String resourcePath = filePath.toString().replace('\\', '/');
        String text;
        try {
            text = Templater.readTemplate(resourcePath);
        } catch (IOException e) {
            send(exchange, 404, "text/plain; charset=UTF-8", "Not found");
            return;
        }

        send(exchange, 200, contentType, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(Exchange exchange, int statusCode, String contentType, String body) throws IOException {
        send(exchange, statusCode, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(Exchange exchange, int statusCode, String contentType, byte[] payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (var out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }
}