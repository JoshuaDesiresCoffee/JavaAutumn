package Implementation.handler;

import Autumn.handler.BaseHandler;
import Autumn.handler.Exchange;
import Autumn.templating.Templater;
import Implementation.repository.User;

import java.io.IOException;
import java.util.Map;

public class IndexHandler {

    public static void get(Exchange exchange) throws IOException {
        exchange.html(Templater.render("index.html", Map.of(
                "title", "JavaAutumn",
                "subtitle", "Minimal Java HTTP demo",
                "message", "Server is running."
        )));
    }

    public static void listUsers(Exchange exchange) throws IOException {
        BaseHandler.renderList(exchange, "user.html", "users", User.class);
    }
}
