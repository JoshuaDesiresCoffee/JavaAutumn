package Autumn.handler;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.URI;

public class Exchange {
    private final HttpExchange raw;

    public Exchange(HttpExchange raw) { this.raw = raw; }

    public String  method()                    { return raw.getRequestMethod(); }
    public URI     uri()                       { return raw.getRequestURI(); }
    public InputStream  body()                 { return raw.getRequestBody(); }
    public Headers getRequestHeaders()            { return raw.getRequestHeaders(); }
    public Headers getResponseHeaders()           { return raw.getResponseHeaders(); }
    public void sendResponseHeaders(int status, long length) throws IOException {
        raw.sendResponseHeaders(status, length);
    }
    public OutputStream getResponseBody()         { return raw.getResponseBody(); }

    public void send(int status, String body) throws IOException {
        byte[] bytes = body.getBytes();
        raw.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = raw.getResponseBody()) { os.write(bytes); }
    }
    public void send(String body)  throws IOException { send(200, body); }
    public void send(int status)   throws IOException { raw.sendResponseHeaders(status, -1); }
}