package Autumn;

import Autumn.handler.Exchange;
import Autumn.handler.Handler;
import Autumn.orm.Db;
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
        Db.init();
    }

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
}