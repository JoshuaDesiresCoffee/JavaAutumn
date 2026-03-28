package Handler;

import Repository.GenericTableRepository;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Api {
    private static final GenericTableRepository REPOSITORY = new GenericTableRepository();
    private static final Pattern JSON_PAIR_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"((?:\\\\.|[^\"\\\\])*)\"|null|true|false|-?\\d+(?:\\.\\d+)?)");

    public static void tables(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            List<String> tables = REPOSITORY.listTables();
            String body = tables.stream()
                    .map(Api::quoteJson)
                    .collect(Collectors.joining(",", "{\"tables\":[", "]}"));
            sendJson(exchange, 200, body);
        } catch (SQLException sqlException) {
            sendJson(exchange, 500, error(sqlException.getMessage()));
        }
    }

    public static void rows(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String table = REPOSITORY.requireKnownTable(query.get("table"));
            String method = exchange.getRequestMethod().toUpperCase();

            switch (method) {
                case "GET" -> handleGetRows(exchange, table);
                case "POST" -> handleCreateRow(exchange, table);
                case "PUT" -> handleUpdateRow(exchange, table, query);
                case "DELETE" -> handleDeleteRow(exchange, table, query);
                default -> sendJson(exchange, 405, error("Method not allowed"));
            }
        } catch (IllegalArgumentException illegalArgumentException) {
            sendJson(exchange, 400, error(illegalArgumentException.getMessage()));
        } catch (SQLException sqlException) {
            sendJson(exchange, 500, error(sqlException.getMessage()));
        }
    }

    private static void handleGetRows(HttpExchange exchange, String table) throws IOException, SQLException {
        List<Map<String, Object>> rows = REPOSITORY.getRows(table);
        String jsonRows = rows.stream()
                .map(Api::toJsonObject)
                .collect(Collectors.joining(","));
        sendJson(exchange, 200, "{\"table\":" + quoteJson(table) + ",\"rows\":[" + jsonRows + "]}");
    }

    private static void handleCreateRow(HttpExchange exchange, String table) throws IOException, SQLException {
        Map<String, String> payload = readJsonBody(exchange);
        REPOSITORY.insertRow(table, payload);
        sendJson(exchange, 201, "{\"status\":\"created\"}");
    }

    private static void handleUpdateRow(HttpExchange exchange, String table, Map<String, String> query) throws IOException, SQLException {
        String id = required(query, "id");
        Map<String, String> payload = readJsonBody(exchange);
        REPOSITORY.updateRow(table, id, payload);
        sendJson(exchange, 200, "{\"status\":\"updated\"}");
    }

    private static void handleDeleteRow(HttpExchange exchange, String table, Map<String, String> query) throws IOException, SQLException {
        String id = required(query, "id");
        REPOSITORY.deleteRow(table, id);
        sendJson(exchange, 200, "{\"status\":\"deleted\"}");
    }

    private static Map<String, String> readJsonBody(HttpExchange exchange) throws IOException {
        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (raw.isEmpty()) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = JSON_PAIR_PATTERN.matcher(raw);
        while (matcher.find()) {
            String key = matcher.group(1);
            String rawValue = matcher.group(2);
            if (rawValue == null) {
                continue;
            }

            if (rawValue.startsWith("\"") && rawValue.endsWith("\"")) {
                String escaped = matcher.group(3) == null ? "" : matcher.group(3);
                values.put(key, escaped
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\n", "\n")
                        .replace("\\t", "\t"));
            } else if ("null".equals(rawValue)) {
                values.put(key, "");
            } else {
                values.put(key, rawValue);
            }
        }
        return values;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            String[] split = pair.split("=", 2);
            String key = urlDecode(split[0]);
            String value = split.length == 2 ? urlDecode(split[1]) : "";
            values.put(key, value);
        }
        return values;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing parameter: " + key);
        }
        return value.trim();
    }

    private static String toJsonObject(Map<String, Object> values) {
        return values.entrySet().stream()
                .map(entry -> quoteJson(entry.getKey()) + ":" + toJsonValue(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String toJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return quoteJson(String.valueOf(value));
    }

    private static String quoteJson(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private static String error(String message) {
        return "{\"error\":" + quoteJson(message == null ? "Unknown error" : message) + "}";
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }
}
