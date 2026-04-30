package Implementation.handler;

import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.ObjectToMapConverter;
import Autumn.templating.Templater;
import Implementation.repository.Rating;
import Implementation.repository.Stars;

import java.io.IOException;
import java.util.*;

public class StarsHandler {

    public static void list(Exchange exchange) throws IOException {
        var starsList = Db.instance.SELECT.FROM(Stars.class).EXEC();
        var ratings   = Db.instance.SELECT.FROM(Rating.class).EXEC();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Stars s : starsList) {
            Map<String, Object> row = ObjectToMapConverter.convert(s);
            if (row == null) continue;
            // Count how many ratings use this stars level
            long ratingCount = ratings.stream().filter(r -> r.starsId == s.id).count();
            row.put("ratingCount", ratingCount);
            rows.add(row);
        }

        exchange.html(Templater.render("stars.html", Map.of("starsList", rows)));
    }

    public static void create(Exchange exchange) throws IOException {
        try {
            String displayedAs = exchange.formParam("displayedAs", "").trim();
            String valueStr    = exchange.formParam("value", "");
            if (displayedAs.isBlank() || valueStr.isBlank()) {
                exchange.send(400, "displayedAs and value are required");
                return;
            }
            Stars s = new Stars();
            s.displayedAs = displayedAs;
            s.value       = Integer.parseInt(valueStr);
            Db.instance.INSERT(s).EXEC();
            exchange.redirect("/stars");
        } catch (Exception e) {
            exchange.send(500, "Failed to create stars: " + e.getMessage());
        }
    }

    public static void delete(Exchange exchange) throws IOException {
        try {
            String idStr = exchange.formParam("id", "");
            if (idStr.isBlank()) idStr = exchange.queryParam("id", "");
            if (idStr.isBlank()) { exchange.send(400, "id is required"); return; }
            Db.instance.DELETE.FROM(Stars.class).BY_ID(Integer.parseInt(idStr)).EXEC();
            exchange.redirect("/stars");
        } catch (Exception e) {
            String msg = e.getMessage() + (e.getCause() != null ? " " + e.getCause().getMessage() : "");
            if (msg.contains("FOREIGN KEY")) {
                exchange.send(400, "Cannot delete Stars: still referenced by ratings.");
            } else {
                exchange.send(500, e.getMessage());
            }
        }
    }
}
