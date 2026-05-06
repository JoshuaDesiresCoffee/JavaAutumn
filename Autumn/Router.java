package Autumn;

import Autumn.handler.Exchange;
import Autumn.handler.Handler;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class Router {
    private final HttpServer server;
    private final Map<String, Map<String, HttpHandler>> routesByPath = new ConcurrentHashMap<>();

    public Router(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void handle(String method, String path, HttpHandler handler) {
        String normalizedMethod = method.toUpperCase(Locale.ROOT);
        Map<String, HttpHandler> handlersForPath = routesByPath.computeIfAbsent(path, key -> {
            server.createContext(path, exchange -> {
                String requestPath = exchange.getRequestURI().getPath();
                boolean prefixRoute = path.endsWith("/") && !path.equals("/");
                boolean pathMatches = prefixRoute
                        ? requestPath.startsWith(path)
                        : requestPath.equals(path);

                if (!pathMatches) {
                    sendError(exchange, 404, "Not found");
                    return;
                }

                Map<String, HttpHandler> handlers = routesByPath.get(path);
                HttpHandler matchedHandler = handlers == null
                        ? null
                        : handlers.get(exchange.getRequestMethod().toUpperCase(Locale.ROOT));

                if (matchedHandler == null) {
                    sendError(exchange, 405, "Method not allowed");
                    return;
                }

                try {
                    matchedHandler.handle(exchange);
                } catch (Exception ex) {
                    System.err.println("Handler failed: " + exchange.getRequestMethod() + " " + requestPath);
                    ex.printStackTrace();
                    sendError(exchange, 500, "Internal server error");
                }
            });
            return new ConcurrentHashMap<>();
        });

        handlersForPath.put(normalizedMethod, handler);
    }

    public void serve() {
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("Server running on port: " + server.getAddress());
    }

    private HttpHandler wrap(Handler h) {
        return exchange -> h.handle(new Exchange(exchange));
    }

    public void GET(String path, Handler h)    { handle("GET",    path, wrap(h)); }
    public void POST(String path, Handler h)   { handle("POST",   path, wrap(h)); }
    public void PUT(String path, Handler h)    { handle("PUT",    path, wrap(h)); }
    public void DELETE(String path, Handler h) { handle("DELETE", path, wrap(h)); }

    private static void sendError(com.sun.net.httpserver.HttpExchange exchange, int status, String message) {
        try {
            byte[] payload = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(status, payload.length);
            try (var out = exchange.getResponseBody()) {
                out.write(payload);
            }
        } catch (IOException ignored) {
        } finally {
            exchange.close();
        }
    }
}
