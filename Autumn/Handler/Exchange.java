package Autumn.handler;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Exchange {
    private final HttpExchange raw;

    public Exchange(HttpExchange raw) { this.raw = raw; }

    public String  method()                    { return raw.getRequestMethod(); }
    public String  path()                      { return raw.getRequestURI().getPath(); }
    public URI     uri()                       { return raw.getRequestURI(); }
    public InputStream  body()                 { return raw.getRequestBody(); }
    public Headers getRequestHeaders()            { return raw.getRequestHeaders(); }
    public Headers getResponseHeaders()           { return raw.getResponseHeaders(); }
    public void sendResponseHeaders(int status, long length) throws IOException {
        raw.sendResponseHeaders(status, length);
    }
    public OutputStream getResponseBody()         { return raw.getResponseBody(); }

    public Optional<String> queryParam(String name) {
        return Optional.ofNullable(parseParams(raw.getRequestURI().getRawQuery()).get(name));
    }

    public String queryParam(String name, String fallback) {
        return queryParam(name).filter(value -> !value.isBlank()).orElse(fallback);
    }

    private Map<String, String> bodyParams = null;

    public Optional<String> formParam(String name) {
        if (bodyParams == null) {
            try {
                String bodyStr = new BufferedReader(new InputStreamReader(body(), StandardCharsets.UTF_8))
                        .lines().collect(Collectors.joining("\n"));
                bodyParams = parseParams(bodyStr);
            } catch (Exception e) {
                bodyParams = Map.of();
            }
        }
        return Optional.ofNullable(bodyParams.get(name));
    }

    public String formParam(String name, String fallback) {
        return formParam(name).filter(value -> !value.isBlank()).orElse(fallback);
    }

    public void html(String body) throws IOException {
        send(200, "text/html; charset=UTF-8", body);
    }

    public void json(String body) throws IOException {
        send(200, "application/json; charset=UTF-8", body);
    }

    public void redirect(String location) throws IOException {
        raw.getResponseHeaders().set("Location", location);
        raw.sendResponseHeaders(302, -1);
    }

    public void send(int status, String body) throws IOException {
        send(status, "text/plain; charset=UTF-8", body);
    }

    public void send(int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        raw.getResponseHeaders().set("Content-Type", contentType);
        raw.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = raw.getResponseBody()) { os.write(bytes); }
    }
    public void send(String body)  throws IOException { send(200, body); }
    public void send(int status)   throws IOException { raw.sendResponseHeaders(status, -1); }

    private static Map<String, String> parseParams(String rawParams) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawParams == null || rawParams.isBlank()) {
            return params;
        }

        for (String pair : rawParams.split("&")) {
            int idx = pair.indexOf('=');
            String rawKey = idx < 0 ? pair : pair.substring(0, idx);
            String rawValue = idx < 0 ? "" : pair.substring(idx + 1);
            params.put(decode(rawKey), decode(rawValue));
        }

        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
