package Implementation.handler;

import Autumn.handler.CrudHandler;
import Autumn.handler.Exchange;
import Implementation.repository.User;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * GET-based JSON API for users. Reads parameters from the query string instead of
 * the form body, but otherwise reuses the standard CRUD machinery.
 */
public class UserAPIHandler extends CrudHandler<User> {

    public UserAPIHandler() {
        super(User.class, "/users", "user.html", "users");
    }

    /** The list endpoint emits JSON, not HTML. */
    @Override
    public void list(Exchange exchange) throws IOException {
        api(exchange);
    }

    /** Bind the entity from query parameters since these endpoints use GET. */
    @Override
    protected User bindFromForm(Exchange exchange, boolean includeId) throws Exception {
        User user = new User();
        for (Field f : User.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
            if (!includeId && "id".equals(f.getName())) continue;
            String raw = exchange.queryParam(f.getName(), "").trim();
            if (raw.isBlank()) continue;
            f.setAccessible(true);
            if (f.getType() == int.class || f.getType() == Integer.class) {
                f.set(user, Integer.parseInt(raw));
            } else {
                f.set(user, raw);
            }
        }
        return user;
    }

    @Override
    protected String validate(User user) {
        if (user.name == null || user.name.isBlank()) return "name is required";
        if (user.email == null || user.email.isBlank()) return "email is required";
        return null;
    }
}
