package Service;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Router {
    private final HttpServer server;
    private final Map<String, Map<String, HttpHandler>> routesByPath = new ConcurrentHashMap<>();

    public Router(HttpServer server) { this.server = server; }

    public void handle(String method, String path, HttpHandler handler) {
        String normalizedMethod = method.toUpperCase(Locale.ROOT);
        Map<String, HttpHandler> handlersForPath = routesByPath.computeIfAbsent(path, key -> {
            server.createContext(path, exchange -> {
                Map<String, HttpHandler> handlers = routesByPath.get(path);
                HttpHandler matchedHandler = handlers == null
                        ? null
                        : handlers.get(exchange.getRequestMethod().toUpperCase(Locale.ROOT));

                if (matchedHandler == null) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                matchedHandler.handle(exchange);
            });
            return new ConcurrentHashMap<>();
        });

        handlersForPath.put(normalizedMethod, handler);
    }

    public void GET(String path, HttpHandler h)    { handle("GET",    path, h); }
    public void POST(String path, HttpHandler h)   { handle("POST",   path, h); }
    public void PUT(String path, HttpHandler h)    { handle("PUT",    path, h); }
    public void DELETE(String path, HttpHandler h) { handle("DELETE", path, h); }
}