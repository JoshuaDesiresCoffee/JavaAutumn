package Implementation.handler;

import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.Json;
import Implementation.repository.User;

import java.io.IOException;

public class UserAPIHandler {

    public static void list(Exchange exchange) throws IOException {
        try {
            var users = Db.instance.SELECT.FROM(User.class).EXEC();
            exchange.json(Json.toJson(users));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    public static void create(Exchange exchange) throws IOException {
        try {
            String name = requestParam(exchange, "name", "");
            String email = requestParam(exchange, "email", "");
            if (name.isBlank() || email.isBlank()) {
                exchange.send(400, "name and email are required");
                return;
            }

            User u = new User();
            u.name = name;
            u.email = email;
            Db.instance.INSERT(u).EXEC();

            exchange.redirect("/users");
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    public static void update(Exchange exchange) throws IOException {
        try {
            String idStr = requestParam(exchange, "id", "");
            String name = requestParam(exchange, "name", "");
            String email = requestParam(exchange, "email", "");
            if (idStr.isBlank() || name.isBlank() || email.isBlank()) {
                exchange.send(400, "id, name and email are required");
                return;
            }

            User u = new User();
            u.id = Integer.parseInt(idStr);
            u.name = name;
            u.email = email;
            Db.instance.UPDATE(u).BY_ID(u.id).EXEC();

            exchange.redirect("/users");
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    public static void delete(Exchange exchange) throws IOException {
        try {
            String idStr = requestParam(exchange, "id", "");
            if (idStr.isBlank()) {
                exchange.send(400, "id is required");
                return;
            }

            Db.instance.DELETE.FROM(User.class).BY_ID(Integer.parseInt(idStr)).EXEC();

            exchange.redirect("/users");
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    private static String requestParam(Exchange exchange, String key, String fallback) {
        return exchange.queryParam(key, fallback).trim();
    }
}
