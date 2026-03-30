package Implementation.handler;

import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.Json;
import Implementation.repository.User;

import java.io.IOException;

public class UserAPIHandler {

    public static void get(Exchange exchange) throws IOException {

        try {
            var u = Db.instance.SELECT.FROM(User.class).LIMIT(1).EXEC();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.send(201, Json.toJson(u));
        } catch (Exception e) {
            e.printStackTrace();
            exchange.send(500, e.getMessage());
        }
    }

    public static void list(Exchange exchange) throws IOException {

        try {
            var u = Db.instance.SELECT.FROM(User.class).EXEC();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.send(201, Json.toJson(u));
        } catch (Exception e) {
            e.printStackTrace();
            exchange.send(500, e.getMessage());
        }
    }

    public static void create(Exchange exchange) throws IOException {

        try {
            var oldUserList = Db.instance.SELECT.FROM(User.class).EXEC();

            User u = new User();
            u.id = oldUserList.size() + 1;
            u.email = "example@mail.com";
            u.name = "Example";

            var newUser = Db.instance.INSERT(u).EXEC();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.send(201, Json.toJson(u));
        } catch (Exception e) {
            e.printStackTrace();
            exchange.send(500, e.getMessage());
        }
    }

    public static void update(Exchange exchange) throws IOException {
        try {
            User u = new User();
            u.id = 1;
            u.email = "updated@mail.com";
            u.name = "Updated";

            Db.instance.UPDATE(u).WHERE("id = " + u.id).EXEC();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.send(200, Json.toJson(u));
        } catch (Exception e) {
            e.printStackTrace();
            exchange.send(500, e.getMessage());
        }
    }

    public static void delete(Exchange exchange) throws IOException {
        try {
            Db.instance.DELETE.FROM(User.class).WHERE("id = 1").EXEC();
            exchange.send(200);
        } catch (Exception e) {
            e.printStackTrace();
            exchange.send(500, e.getMessage());
        }
    }
}
