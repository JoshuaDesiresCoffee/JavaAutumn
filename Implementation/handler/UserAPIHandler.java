package Implementation.handler;

import Autumn.handler.CrudHandler;
import Autumn.handler.Exchange;
import Implementation.repository.User;

import java.io.IOException;

/**
 * User REST-ish endpoints: {@code GET /api/user/all} returns JSON; create/update/delete use
 * {@code POST} with {@code application/x-www-form-urlencoded} bodies ({@link CrudHandler} form binding).
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

    @Override
    protected String validate(User user) {
        if (user.name == null || user.name.isBlank()) return "name is required";
        if (user.email == null || user.email.isBlank()) return "email is required";
        return null;
    }
}
