package Implementation.handler;

import Autumn.handler.BaseHandler;
import Autumn.handler.CrudHandler;
import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Implementation.repository.Role;
import Implementation.repository.User;
import Implementation.repository.UserRole;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RoleHandler extends CrudHandler<Role> {

    public RoleHandler() {
        super(Role.class, "/roles", "roles.html", "roles");
    }

    /** Eager-load the {@link UserRole} junction rows so {@link #decorateRow} can size the user list. */
    @Override
    protected List<Role> selectAll() {
        return Db.instance.SELECT.FROM(Role.class).JOIN(UserRole.class).EXEC();
    }

    @Override
    protected void decorateRow(Role role, Map<String, Object> row) {
        row.put("userCount", role.userRoles == null ? 0 : role.userRoles.size());
    }

    @Override
    protected Map<String, Object> extraListContext() {
        return Map.of("users", BaseHandler.selectAllRows(User.class));
    }

    /** Force role names to upper-case before insert. */
    @Override
    protected Role bindFromForm(Exchange exchange, boolean includeId) throws Exception {
        Role role = super.bindFromForm(exchange, includeId);
        if (role.displayedAs != null) role.displayedAs = role.displayedAs.toUpperCase();
        return role;
    }

    @Override
    protected String validate(Role role) {
        if (role.displayedAs == null || role.displayedAs.isBlank()) return "Role name is required";
        return null;
    }

    public void assignUser(Exchange exchange) throws IOException {
        Optional<Integer> userId = BaseHandler.intFormParam(exchange, "userId");
        Optional<Integer> roleId = BaseHandler.intFormParam(exchange, "roleId");
        if (userId.isEmpty() || roleId.isEmpty()) {
            exchange.send(400, "userId and roleId are required (numeric)");
            return;
        }
        try {
            UserRole probe = new UserRole();
            probe.user = Db.instance.stub(User.class, userId.get());
            probe.role = Db.instance.stub(Role.class, roleId.get());
            if (Db.instance.SELECT.FROM(UserRole.class).WHERE(probe).LIMIT(1).EXEC().isEmpty()) {
                Db.instance.INSERT.INTO(UserRole.class).VALUES(probe).EXEC();
            }
            exchange.redirect(routePrefix);
        } catch (Exception e) {
            exchange.send(500, "Failed to assign role: " + e.getMessage());
        }
    }

    public void removeUser(Exchange exchange) throws IOException {
        Optional<Integer> idOpt = BaseHandler.idParam(exchange);
        if (idOpt.isEmpty()) {
            exchange.send(400, "id is required");
            return;
        }
        try {
            Db.instance.DELETE.FROM(UserRole.class).WHERE("id = ?", idOpt.get()).EXEC();
            exchange.redirect(routePrefix);
        } catch (Exception e) {
            BaseHandler.renderDeleteError(exchange, e);
        }
    }
}
