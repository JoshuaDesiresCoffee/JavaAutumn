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
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Stateless utility helpers shared by all handlers.
 *
 * <p>Use {@link CrudHandler} when you want full list/create/update/delete
 * routing for an entity. Reach for {@code BaseHandler} only when you need a
 * handful of helpers from a custom handler (e.g. {@code IndexHandler}).
 */
public final class BaseHandler {

    private BaseHandler() {}

    // ---------------------------------------------------------------------
    // Template / row helpers
    // ---------------------------------------------------------------------

    /** Convert a list of entities to a list of template-friendly maps. */
    public static <E> List<Map<String, Object>> toRows(List<E> entities) {
        List<Map<String, Object>> rows = new ArrayList<>(entities.size());
        for (E entity : entities) {
            Map<String, Object> row = ObjectToMapConverter.convert(entity);
            if (row != null) rows.add(row);
        }
        return rows;
    }

    /** Select all rows of {@code clazz} and return them as template-friendly maps. */
    public static <E> List<Map<String, Object>> selectAllRows(Class<E> clazz) {
        return toRows(Db.instance.SELECT.FROM(clazz).EXEC());
    }

    /**
     * Map primary key → label for lookup tables (same id resolution as the ORM uses for @Id).
     */
    public static <E> Map<Integer, String> mapIds(List<E> rows, Class<E> entityClass, Function<E, String> label) {
        if (rows.isEmpty()) return Map.of();
        FieldInfo idField = EntityMapper.getIdField(entityClass);
        Map<Integer, String> out = HashMap.newHashMap(rows.size());
        for (E row : rows) {
            try {
                Object idObj = idField.field.get(row);
                int id = ((Number) idObj).intValue();
                out.put(id, label.apply(row));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return out;
    }

    /**
     * Counts rows grouped by an int key (e.g. {@code roleId} on junction rows). Use after
     * {@code SELECT.FROM(Junction.class).EXEC()}; the ORM does not infer this from entity shape alone.
     */
    public static <T> Map<Integer, Long> countByIntKey(List<T> rows, ToIntFunction<T> key) {
        if (rows.isEmpty()) return Map.of();
        Map<Integer, Long> m = HashMap.newHashMap(rows.size());
        for (T row : rows) {
            m.merge(key.applyAsInt(row), 1L, Long::sum);
        }
        return m;
    }

    /** Render {@code template} with a single key bound to all rows of {@code clazz}. */
    public static <E> void renderList(Exchange exchange, String template, String key, Class<E> clazz)
            throws IOException {
        exchange.html(Templater.render(template, Map.of(key, selectAllRows(clazz))));
    }

    // ---------------------------------------------------------------------
    // Request helpers
    // ---------------------------------------------------------------------

    /** Read an id from the form body, falling back to the query string. */
    public static Optional<Integer> idParam(Exchange exchange) {
        return intFormParam(exchange, "id");
    }

    /** Read an int from the form body, falling back to the query string. Empty/non-numeric → empty. */
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

    // ---------------------------------------------------------------------
    // Error helpers
    // ---------------------------------------------------------------------

    /** Render the standard error template with {@code message}. */
    public static void renderError(Exchange exchange, String message) throws IOException {
        exchange.html(Templater.render("error.html", Map.of("errorMessage", message)));
    }

    /** Translate a delete failure into a friendly error page. */
    public static void renderDeleteError(Exchange exchange, Exception e) throws IOException {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (e.getCause() != null && e.getCause().getMessage() != null) {
            msg += " " + e.getCause().getMessage();
        }
        if (msg.contains("FOREIGN KEY")) {
            renderError(exchange, "Cannot delete because it is still referenced by other records.");
        } else {
            renderError(exchange, e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // Detail page
    // ---------------------------------------------------------------------

    /** Generic detail page rendering, factored out so any handler can reuse it. */
    public static <E> void renderDetail(Exchange exchange, String entityName, Class<E> clazz)
            throws IOException {
        try {
            String idStr = exchange.queryParam("id", "");
            if (idStr.isBlank()) {
                exchange.send(400, "Missing id parameter.");
                return;
            }
            int id = Integer.parseInt(idStr);

            // JOIN every FK relation so nested objects come back hydrated (no N+1, no stubs).
            SelectQuery<E> q = Db.instance.SELECT.FROM(clazz).WHERE("id = ?", id).LIMIT(1);
            for (FieldInfo fi : EntityMapper.getFields(clazz)) {
                if (fi.isForeignKey && fi.relatedType != null) q = q.JOIN(fi.relatedType);
            }
            List<E> results = q.EXEC();
            if (results.isEmpty()) {
                exchange.send(404, entityName + " not found.");
                return;
            }
            E entity = results.get(0);

            List<Map<String, Object>> fields = new ArrayList<>();
            String pictureUrl = "";
            String bioUrl = "";
            String displayedAs = "Detail";

            for (Field f : clazz.getDeclaredFields()) {
                f.setAccessible(true);
                String name = f.getName();
                Object val = f.get(entity);
                if (val == null) val = "-";

                if (name.equals("pictureUrl")) {
                    if (!val.equals("-") && !val.toString().isBlank()) {
                        pictureUrl = val.toString();
                    }
                    continue;
                }
                if (name.equals("bioUrl")) {
                    if (!val.equals("-") && !val.toString().isBlank()) {
                        bioUrl = val.toString();
                    }
                    continue;
                }
                if (name.equals("displayedAs")) {
                    displayedAs = val.toString();
                }
                fields.add(Map.of("key", name, "value", labelFor(val)));
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

            exchange.html(Templater.render("detail.html", ctx));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // Form binding
    // ---------------------------------------------------------------------

    /** Coerce a raw form/query value to the target field type. */
    public static Object coerce(String raw, Class<?> type) {
        String value = raw.trim();
        if (type == String.class)                          return value;
        if (type == int.class     || type == Integer.class) return Integer.parseInt(value);
        if (type == long.class    || type == Long.class)    return Long.parseLong(value);
        if (type == double.class  || type == Double.class)  return Double.parseDouble(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        return value;
    }

    /**
     * Build a stub of {@code relatedType} carrying just the {@code @Id} field set to {@code id}.
     * Used to bind FK fields from form values like {@code artistId=42}.
     */
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

    /** Renders an FK object as its {@code displayedAs} (or {@code name}) value; non-FK values pass through. */
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
