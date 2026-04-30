package Implementation.handler;

import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.ObjectToMapConverter;
import Autumn.templating.Templater;
import Implementation.repository.Role;
import Implementation.repository.User;
import Implementation.repository.UserRole;

import java.io.IOException;
import java.util.*;

public class RoleHandler {

    public static void list(Exchange exchange) throws IOException {
        var roles = Db.instance.SELECT.FROM(Role.class).EXEC();
        var users = Db.instance.SELECT.FROM(User.class).EXEC();
        var userRoles = Db.instance.SELECT.FROM(UserRole.class).EXEC();

        List<Map<String, Object>> roleRows = new ArrayList<>();
        for (Role role : roles) {
            Map<String, Object> row = ObjectToMapConverter.convert(role);
            if (row == null) continue;
            // Count users with this role
            long userCount = userRoles.stream().filter(ur -> ur.roleId == role.id).count();
            row.put("userCount", userCount);
            roleRows.add(row);
        }

        List<Map<String, Object>> userRows = new ArrayList<>();
        for (User user : users) {
            Map<String, Object> row = ObjectToMapConverter.convert(user);
            if (row != null) userRows.add(row);
        }

        exchange.html(Templater.render("roles.html", Map.of(
            "roles", roleRows,
            "users", userRows,
            "userRoles", userRoles.stream()
                .map(ur -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("userId", ur.userId);
                    m.put("roleId", ur.roleId);
                    return m;
                }).toList()
        )));
    }

    public static void create(Exchange exchange) throws IOException {
        try {
            String name = exchange.formParam("displayedAs", "").trim().toUpperCase();
            if (name.isBlank()) {
                exchange.send(400, "Role name is required");
                return;
            }
            Role role = new Role();
            role.displayedAs = name;
            Db.instance.INSERT(role).EXEC();
            exchange.redirect("/roles");
        } catch (Exception e) {
            exchange.send(500, "Failed to create role: " + e.getMessage());
        }
    }

    public static void delete(Exchange exchange) throws IOException {
        try {
            String idStr = exchange.formParam("id", "");
            if (idStr.isBlank()) idStr = exchange.queryParam("id", "");
            if (idStr.isBlank()) { exchange.send(400, "id is required"); return; }
            int id = Integer.parseInt(idStr);
            Db.instance.DELETE.FROM(Role.class).BY_ID(id).EXEC();
            exchange.redirect("/roles");
        } catch (Exception e) {
            String msg = e.getMessage() + (e.getCause() != null ? " " + e.getCause().getMessage() : "");
            if (msg.contains("FOREIGN KEY")) {
                exchange.html(Autumn.templating.Templater.render("error.html", java.util.Map.of("errorMessage", "Cannot delete Role: still assigned to users.")));
            } else {
                exchange.html(Autumn.templating.Templater.render("error.html", java.util.Map.of("errorMessage", e.getMessage())));
            }
        }
    }

    public static void assignUser(Exchange exchange) throws IOException {
        try {
            String roleIdStr = exchange.formParam("roleId", "");
            String userIdStr = exchange.formParam("userId", "");
            if (roleIdStr.isBlank() || userIdStr.isBlank()) {
                exchange.send(400, "roleId and userId are required");
                return;
            }
            int roleId = Integer.parseInt(roleIdStr);
            int userId = Integer.parseInt(userIdStr);

            // Prevent duplicate
            var existing = Db.instance.SELECT.FROM(UserRole.class).EXEC();
            for (UserRole ur : existing) {
                if (ur.userId == userId && ur.roleId == roleId) {
                    exchange.redirect("/roles");
                    return;
                }
            }
            UserRole ur = new UserRole();
            ur.userId = userId;
            ur.roleId = roleId;
            Db.instance.INSERT(ur).EXEC();
            exchange.redirect("/roles");
        } catch (Exception e) {
            exchange.send(500, "Failed to assign role: " + e.getMessage());
        }
    }

    public static void removeUser(Exchange exchange) throws IOException {
        try {
            String idStr = exchange.formParam("id", "");
            if (idStr.isBlank()) { exchange.send(400, "id is required"); return; }
            Db.instance.DELETE.FROM(UserRole.class).BY_ID(Integer.parseInt(idStr)).EXEC();
            exchange.redirect("/roles");
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }
}
