package Implementation.handler;

import Autumn.handler.BaseHandler;
import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.ObjectToMapConverter;
import Autumn.templating.Templater;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DetailHandler {

    private DetailHandler() {}

    public static <E> void renderDetail(Exchange exchange, String entityName, Class<E> clazz)
            throws IOException {
        try {
            Optional<Integer> idOpt = BaseHandler.idParam(exchange);
            if (idOpt.isEmpty()) {
                exchange.send(400, "Missing id parameter.");
                return;
            }
            int id = idOpt.get();
            List<E> hits = Db.instance.SELECT.FROM(clazz).JOIN_ALL()
                    .WHERE("id = ?", id).LIMIT(1).EXEC();
            if (hits.isEmpty()) {
                exchange.send(404, entityName + " not found.");
                return;
            }
            exchange.html(Templater.render("detail.html",
                    buildDetailContext(hits.get(0), entityName, id)));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    private static Map<String, Object> buildDetailContext(Object entity, String entityName, int id) {
        Map<String, Object> entityMap = ObjectToMapConverter.convert(entity);

        // pictureUrl and bioUrl are rendered at fixed slots in the template, not in the field table.
        List<Map<String, Object>> fields = new ArrayList<>();
        for (Map.Entry<String, Object> e : entityMap.entrySet()) {
            String key = e.getKey();
            if (key.equals("pictureUrl") || key.equals("bioUrl")) continue;
            fields.add(field(key, e.getValue()));
        }

        // determine edit/delete URLs per entity type
        String editUrl   = "";
        String deleteUrl = "";
        boolean canEdit  = false;
        String lname = entityName.toLowerCase();
        if (lname.equals("artist")) {
            editUrl   = "/artists/edit?id=" + id;
            deleteUrl = "/artists/delete";
            canEdit   = true;
        } else if (lname.equals("artwork")) {
            editUrl   = "/artworks/edit?id=" + id;
            deleteUrl = "/artworks/delete";
            canEdit   = true;
        } else if (lname.equals("provenance")) {
            editUrl   = "/provenances/edit?id=" + id;
            deleteUrl = "/provenances/delete";
            canEdit   = true;
        }

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("entityName",    entityName);
        ctx.put("entityId",      id);
        ctx.put("displayedAs",   str(entityMap.get("displayedAs"), "Detail"));
        ctx.put("pictureUrl",    str(entityMap.get("pictureUrl"), ""));
        ctx.put("bioUrl",        str(entityMap.get("bioUrl"), ""));
        ctx.put("editUrl",       editUrl);
        ctx.put("deleteUrl",     deleteUrl);
        ctx.put("canEdit",       canEdit);
        ctx.put("showArtworkEdit", entityName.equalsIgnoreCase("Artwork"));
        ctx.put("artworkEditUrl",  "/artworks/edit?id=" + id);
        ctx.put("fields",        fields);
        return ctx;
    }

    /** {@code Map.of} can't hold null values, so wrap with a tolerant builder. */
    private static Map<String, Object> field(String key, Object value) {
        Map<String, Object> row = new HashMap<>(2);
        row.put("key", key);
        row.put("value", value);
        return row;
    }

    private static String str(Object v, String fallback) {
        return v == null || v.toString().isBlank() ? fallback : v.toString();
    }
}
