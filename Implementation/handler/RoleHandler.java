package Implementation.handler;

import Autumn.handler.BaseHandler;
import Autumn.handler.CrudHandler;
import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.ObjectToMapConverter;
import Autumn.templating.Templater;
import Implementation.repository.Role;
import Implementation.repository.User;
import Implementation.repository.UserRole;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RoleHandler extends CrudHandler<Role> {

    public RoleHandler() {
        super(Role.class, "/roles", "roles.html", "roles");
    }

    /** List joins roles with user counts, so we override the default list. */
    @Override
    public void list(Exchange exchange) throws IOException {
        try {
            List<Role> roles = Db.instance.SELECT.FROM(Role.class).EXEC();
            List<UserRole> userRoles = Db.instance.SELECT.FROM(UserRole.class).EXEC();

            List<Map<String, Object>> roleRows = new ArrayList<>();
            for (Role role : roles) {
                Map<String, Object> row = ObjectToMapConverter.convert(role);
                if (row == null) continue;
                row.put("userCount", userRoles.stream().filter(ur -> ur.roleId == role.id).count());
                roleRows.add(row);
            }

            List<Map<String, Object>> userRoleRows = new ArrayList<>();
            for (UserRole ur : userRoles) {
                Map<String, Object> m = new HashMap<>();
                m.put("userId", ur.userId);
                m.put("roleId", ur.roleId);
                userRoleRows.add(m);
            }

            exchange.html(Templater.render(listTemplate, Map.of(
                    "roles", roleRows,
                    "users", BaseHandler.selectAllRows(User.class),
                    "userRoles", userRoleRows
            )));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
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
        try {
            String roleIdStr = exchange.formParam("roleId", "");
            String userIdStr = exchange.formParam("userId", "");
            if (roleIdStr.isBlank() || userIdStr.isBlank()) {
                exchange.send(400, "roleId and userId are required");
                return;
            }
            int roleId = Integer.parseInt(roleIdStr);
            int userId = Integer.parseInt(userIdStr);

            for (UserRole existing : Db.instance.SELECT.FROM(UserRole.class).EXEC()) {
                if (existing.userId == userId && existing.roleId == roleId) {
                    exchange.redirect(routePrefix);
                    return;
                }
            }
            UserRole ur = new UserRole();
            ur.userId = userId;
            ur.roleId = roleId;
            Db.instance.INSERT(ur).EXEC();
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
            Db.instance.DELETE.FROM(UserRole.class).BY_ID(idOpt.get()).EXEC();
            exchange.redirect(routePrefix);
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }
}
