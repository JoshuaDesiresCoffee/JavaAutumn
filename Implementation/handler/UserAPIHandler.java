package Implementation.handler;

import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.Json;
import Implementation.repository.User;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class UserAPIHandler {

    public static void list(Exchange exchange) throws IOException {
        try {
            var users = Db.instance.SELECT.FROM(User.class).EXEC();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.send(200, Json.toJson(users));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    public static void create(Exchange exchange) throws IOException {
        try {
            String name = param(exchange, "name", "");
            String email = param(exchange, "email", "");
            if (name.isBlank() || email.isBlank()) {
                exchange.send(400, "name and email are required");
                return;
            }

            User u = new User();
            u.name = name;
            u.email = email;
            Db.instance.INSERT(u).EXEC();

            exchange.getResponseHeaders().set("Location", "/users");
            exchange.sendResponseHeaders(302, -1);
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    public static void update(Exchange exchange) throws IOException {
        try {
            String idStr = param(exchange, "id", "");
            String name = param(exchange, "name", "");
            String email = param(exchange, "email", "");
            if (idStr.isBlank() || name.isBlank() || email.isBlank()) {
                exchange.send(400, "id, name and email are required");
                return;
            }

            User u = new User();
            u.id = Integer.parseInt(idStr);
            u.name = name;
            u.email = email;
            Db.instance.UPDATE(u).WHERE("id = " + u.id).EXEC();

            exchange.getResponseHeaders().set("Location", "/users");
            exchange.sendResponseHeaders(302, -1);
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    public static void delete(Exchange exchange) throws IOException {
        try {
            String idStr = param(exchange, "id", "");
            if (idStr.isBlank()) {
                exchange.send(400, "id is required");
                return;
            }

            Db.instance.DELETE.FROM(User.class).WHERE("id = " + Integer.parseInt(idStr)).EXEC();

            exchange.getResponseHeaders().set("Location", "/users");
            exchange.sendResponseHeaders(302, -1);
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    private static String param(Exchange exchange, String key, String fallback) {
        String raw = exchange.uri().getRawQuery();
        if (raw == null || raw.isBlank()) return fallback;
        for (String pair : raw.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) continue;
            String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            if (!k.equals(key)) continue;
            String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8).trim();
            return v.isBlank() ? fallback : v;
        }
        return fallback;
    }
}
