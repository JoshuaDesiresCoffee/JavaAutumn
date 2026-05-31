package Autumn.handler;

import Autumn.orm.Db;
import Autumn.templating.ObjectToMapConverter;
import Autumn.templating.Templater;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stateless helpers shared by handlers. */
public final class BaseHandler {

    private BaseHandler() {}

    public static <E> List<Map<String, Object>> toRows(List<E> entities) {
        List<Map<String, Object>> rows = new ArrayList<>(entities.size());
        for (E entity : entities) {
            Map<String, Object> row = ObjectToMapConverter.convert(entity);
            if (row != null) rows.add(row);
        }
        return rows;
    }

    public static <E> List<Map<String, Object>> selectAllRows(Class<E> clazz) {
        return toRows(Db.instance.SELECT.FROM(clazz).EXEC());
    }

    public static <E> void renderList(Exchange exchange, String template, String key, Class<E> clazz)
            throws IOException {
        exchange.html(Templater.render(template, Map.of(key, selectAllRows(clazz))));
    }

    public static Optional<Integer> idParam(Exchange exchange) {
        return intFormParam(exchange, "id");
    }

    public static Optional<Integer> intFormParam(Exchange exchange, String name) {
        String s = exchange.formParam(name, "");
        if (s.isBlank()) s = exchange.queryParam(name, "");
        if (s.isBlank()) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(s.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static void renderError(Exchange exchange, String message) throws IOException {
        exchange.html(Templater.render("error.html", Map.of("errorMessage", message)));
    }

    public static void renderDeleteError(Exchange exchange, Exception e) throws IOException {
        if (isForeignKeyViolation(e)) {
            renderError(exchange, "Cannot delete because it is still referenced by other records.");
        } else {
            renderError(exchange, e.getMessage());
        }
    }

    private static boolean isForeignKeyViolation(Throwable t) {
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null && msg.contains("FOREIGN KEY")) return true;
            t = t.getCause();
        }
        return false;
    }

    public interface DetailRenderer {
        void renderDetail(Exchange exchange, String entityName, Class<?> clazz) throws IOException;
    }

    public static DetailRenderer detailRenderer;

    public static void renderDetail(Exchange exchange, String entityName, Class<?> clazz) throws IOException {
        if (detailRenderer != null) {
            detailRenderer.renderDetail(exchange, entityName, clazz);
        } else {
            exchange.send(500, "Detail renderer not configured.");
        }
    }

    /** Parses a form value into the given target type. */
    public static Object coerce(String raw, Class<?> type) {
        String value = raw.trim();
        if (type == String.class)                          return value;
        if (type == int.class     || type == Integer.class) return Integer.parseInt(value);
        if (type == long.class    || type == Long.class)    return Long.parseLong(value);
        if (type == double.class  || type == Double.class)  return Double.parseDouble(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        return value;
    }
}
