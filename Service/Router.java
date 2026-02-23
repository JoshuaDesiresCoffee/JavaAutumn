package Service;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Router {
    private final HttpServer server;

    public Router(HttpServer server) { this.server = server; }

    public void handle(String method, String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            handler.handle(exchange);
        });
    }

    public void GET(String path, HttpHandler h)    { handle("GET",    path, h); }
    public void POST(String path, HttpHandler h)   { handle("POST",   path, h); }
    public void PUT(String path, HttpHandler h)    { handle("PUT",    path, h); }
    public void DELETE(String path, HttpHandler h) { handle("DELETE", path, h); }
}