package Autumn.handler;

import Autumn.orm.Db;
import Autumn.templating.ObjectToMapConverter;
import Autumn.templating.Templater;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        String s = exchange.formParam("id", "");
        if (s.isBlank()) s = exchange.queryParam("id", "");
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

            List<E> results = Db.instance.SELECT.FROM(clazz).WHERE("id = ?", id).EXEC();
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
                    fields.add(Map.of("key", name, "value", val));
                } else {
                    fields.add(Map.of("key", name, "value", val));
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
}
