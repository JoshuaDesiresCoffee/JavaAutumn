package Implementation.handler;

import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.Templater;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityDetailHandler {

    public static <T> void render(Exchange exchange, String entityName, Class<T> clazz) throws IOException {
        try {
            String idStr = exchange.queryParam("id", "");
            if (idStr.isBlank()) {
                exchange.send(400, "Missing id parameter.");
                return;
            }
            int id = Integer.parseInt(idStr);

            List<T> results = Db.instance.SELECT.FROM(clazz).WHERE("id = ?", id).EXEC();
            if (results.isEmpty()) {
                exchange.send(404, entityName + " not found.");
                return;
            }

            T entity = results.get(0);

            List<Map<String, Object>> fields = new ArrayList<>();
            String pictureHtml = "";
            String bioHtml = "";
            String displayedAs = "Detail";

            for (Field f : clazz.getDeclaredFields()) {
                f.setAccessible(true);
                String name = f.getName();
                Object val = f.get(entity);
                if (val == null) val = "-";

                if (name.equals("pictureUrl") && !val.equals("-") && !val.toString().isBlank()) {
                    pictureHtml = "<img src=\"" + val.toString() + "\" class=\"detail-image\" alt=\"Picture\" referrerpolicy=\"no-referrer\" onerror=\"this.style.display='none';\">";
                } else if (name.equals("bioUrl") && !val.equals("-") && !val.toString().isBlank()) {
                    bioHtml = "<a href=\"" + val.toString() + "\" target=\"_blank\" class=\"table-link\" style=\"display:inline-block; width: 100%; box-sizing: border-box;\">Read Biography ↗</a>";
                } else if (name.equals("displayedAs")) {
                    displayedAs = val.toString();
                    fields.add(Map.of("key", name, "value", val));
                } else {
                    fields.add(Map.of("key", name, "value", val));
                }
            }

            String editHtml = "";
            if (entityName.equalsIgnoreCase("Artwork")) {
                editHtml = "<a href=\"/artworks/edit?id=" + id + "\" class=\"table-link\" style=\"background: var(--accent-orange); color: white;\">✏️ Edit</a>";
            }

            Map<String, Object> ctx = new HashMap<>();
            ctx.put("entityName", entityName);
            ctx.put("id", id);
            ctx.put("displayedAs", displayedAs);
            ctx.put("pictureHtml", pictureHtml);
            ctx.put("bioHtml", bioHtml);
            ctx.put("editHtml", editHtml);
            ctx.put("fields", fields);
            // Lowercase entity type for delete route e.g., /artworks/delete -> needs plural. We'll just provide a back link for now.
            
            exchange.html(Templater.render("detail.html", ctx));

        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }
}
