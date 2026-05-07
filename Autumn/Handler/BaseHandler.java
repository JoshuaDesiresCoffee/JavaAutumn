package Autumn.handler;

import Autumn.orm.Db;
import Autumn.orm.Table;
import Autumn.orm.mapping.EntityMapper;
import Autumn.orm.mapping.EntityMapper.FieldInfo;
import Autumn.orm.query.SelectQuery;
import Autumn.templating.ObjectToMapConverter;
import Autumn.templating.Templater;

import java.io.IOException;
import java.lang.reflect.Field;
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

    public static <E> void renderDetail(Exchange exchange, String entityName, Class<E> clazz)
            throws IOException {
        try {
            String idStr = exchange.queryParam("id", "");
            if (idStr.isBlank()) {
                exchange.send(400, "Missing id parameter.");
                return;
            }
            int id = Integer.parseInt(idStr);
            E entity = loadWithJoins(clazz, id);
            if (entity == null) {
                exchange.send(404, entityName + " not found.");
                return;
            }
            exchange.html(Templater.render("detail.html", buildDetailContext(entity, entityName, id, clazz)));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    private static <E> E loadWithJoins(Class<E> clazz, int id) {
        SelectQuery<E> q = Db.instance.SELECT.FROM(clazz).WHERE("id = ?", id).LIMIT(1);
        for (FieldInfo fi : EntityMapper.getFields(clazz)) {
            if (fi.isForeignKey && fi.relatedType != null) q = q.JOIN(fi.relatedType);
        }
        List<E> results = q.EXEC();
        return results.isEmpty() ? null : results.get(0);
    }

    private static <E> Map<String, Object> buildDetailContext(E entity, String entityName, int id, Class<E> clazz)
            throws IllegalAccessException {
        List<Map<String, Object>> fields = new ArrayList<>();
        String pictureUrl = "";
        String bioUrl = "";
        String displayedAs = "Detail";

        for (Field f : clazz.getDeclaredFields()) {
            f.setAccessible(true);
            String name = f.getName();
            Object val = f.get(entity);

            switch (name) {
                case "pictureUrl":
                    if (val != null && !val.toString().isBlank()) pictureUrl = val.toString();
                    break;
                case "bioUrl":
                    if (val != null && !val.toString().isBlank()) bioUrl = val.toString();
                    break;
                case "displayedAs":
                    if (val != null) displayedAs = val.toString();
                    fields.add(Map.of("key", name, "value", labelFor(val)));
                    break;
                default:
                    fields.add(Map.of("key", name, "value", labelFor(val)));
            }
        }

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("entityName", entityName);
        ctx.put("id", id);
        ctx.put("displayedAs", displayedAs);
        ctx.put("pictureUrl", pictureUrl);
        ctx.put("bioUrl", bioUrl);
        ctx.put("showArtworkEdit", entityName.equalsIgnoreCase("Artwork"));
        ctx.put("artworkEditUrl", "/artworks/edit?id=" + id);
        ctx.put("fields", fields);
        return ctx;
    }

    public static Object coerce(String raw, Class<?> type) {
        String value = raw.trim();
        if (type == String.class)                          return value;
        if (type == int.class     || type == Integer.class) return Integer.parseInt(value);
        if (type == long.class    || type == Long.class)    return Long.parseLong(value);
        if (type == double.class  || type == Double.class)  return Double.parseDouble(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        return value;
    }

    /** Builds an entity with only the id field set, used for FK form binding. */
    public static Object stubWithId(Class<?> relatedType, int id) {
        try {
            Object stub = relatedType.getDeclaredConstructor().newInstance();
            FieldInfo idField = EntityMapper.getIdField(relatedType);
            idField.field.set(stub, BaseHandler.coerce(Integer.toString(id), idField.field.getType()));
            return stub;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to build stub for " + relatedType.getSimpleName(), e);
        }
    }

    private static Object labelFor(Object val) {
        if (val == null) return "-";
        Class<?> c = val.getClass();
        if (!c.isAnnotationPresent(Table.class)) return val;
        for (String candidate : new String[]{"displayedAs", "name"}) {
            try {
                Field f = c.getDeclaredField(candidate);
                f.setAccessible(true);
                Object label = f.get(val);
                if (label != null && !label.toString().isBlank()) return label;
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return "(linked)";
    }
}
