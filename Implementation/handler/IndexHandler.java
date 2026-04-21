package Implementation.handler;

import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.ObjectToMapConverter;
import Autumn.templating.Templater;
import Implementation.repository.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class IndexHandler {

    public static void get(Exchange exchange) throws IOException {
        Map<String, Object> view = Map.of(
                "title", "JavaAutumn",
                "subtitle", "Minimal Java HTTP demo",
                "message", "Server is running."
        );

        String html = Templater.render("index.html", view);
        exchange.html(html);
    }

    public static void listUsers(Exchange exchange) throws IOException {
        var users = Db.instance.SELECT.FROM(User.class).EXEC();
        List<Map<String, Object>> userRows = new ArrayList<>(users.size());
        for (User user : users) {
            Map<String, Object> row = ObjectToMapConverter.convert(user);
            if (row != null) {
                userRows.add(row);
            }
        }

        String html = Templater.render("user.html", Map.of("users", userRows));
        exchange.html(html);
    }
}
