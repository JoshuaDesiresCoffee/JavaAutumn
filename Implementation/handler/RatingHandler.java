package Implementation.handler;

import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.ObjectToMapConverter;
import Autumn.templating.Templater;
import Implementation.repository.*;

import java.io.IOException;
import java.util.*;

public class RatingHandler {

    public static void list(Exchange exchange) throws IOException {
        var ratings   = Db.instance.SELECT.FROM(Rating.class).EXEC();
        var artworks  = Db.instance.SELECT.FROM(Artwork.class).EXEC();
        var users     = Db.instance.SELECT.FROM(User.class).EXEC();
        var starsList = Db.instance.SELECT.FROM(Stars.class).EXEC();

        Map<Integer, String> artworkNames = new HashMap<>();
        for (Artwork a : artworks) artworkNames.put(a.id, a.displayedAs);

        Map<Integer, String> userNames = new HashMap<>();
        for (User u : users) userNames.put(u.id, u.name);

        Map<Integer, String> starsDisplay = new HashMap<>();
        for (Stars s : starsList) starsDisplay.put(s.id, s.displayedAs);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Rating r : ratings) {
            Map<String, Object> row = ObjectToMapConverter.convert(r);
            if (row == null) continue;
            row.put("artworkName", artworkNames.getOrDefault(r.artworkId, ""));
            row.put("userName",    userNames.getOrDefault(r.userId, ""));
            row.put("starsLabel",  starsDisplay.getOrDefault(r.starsId, ""));
            rows.add(row);
        }

        List<Map<String, Object>> artworkRows = new ArrayList<>();
        for (Artwork a : artworks) {
            Map<String, Object> m = ObjectToMapConverter.convert(a);
            if (m != null) artworkRows.add(m);
        }

        List<Map<String, Object>> userRows = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> m = ObjectToMapConverter.convert(u);
            if (m != null) userRows.add(m);
        }

        List<Map<String, Object>> starsRows = new ArrayList<>();
        for (Stars s : starsList) {
            Map<String, Object> m = ObjectToMapConverter.convert(s);
            if (m != null) starsRows.add(m);
        }

        exchange.html(Templater.render("ratings.html", Map.of(
            "ratings",  rows,
            "artworks", artworkRows,
            "users",    userRows,
            "starsList", starsRows
        )));
    }

    public static void create(Exchange exchange) throws IOException {
        try {
            String artworkIdStr = exchange.formParam("artworkId", "");
            String userIdStr    = exchange.formParam("userId", "");
            String starsIdStr   = exchange.formParam("starsId", "");
            String displayedAs  = exchange.formParam("displayedAs", "").trim();

            if (artworkIdStr.isBlank() || userIdStr.isBlank() || starsIdStr.isBlank()) {
                exchange.send(400, "artworkId, userId and starsId are required");
                return;
            }

            int artworkId = Integer.parseInt(artworkIdStr);
            int userId    = Integer.parseInt(userIdStr);
            int starsId   = Integer.parseInt(starsIdStr);

            // Uniqueness check
            var existing = Db.instance.SELECT.FROM(Rating.class).EXEC();
            for (Rating r : existing) {
                if (r.userId == userId && r.artworkId == artworkId) {
                    exchange.send(400, "Error: User has already rated this artwork.");
                    return;
                }
            }

            // Auto-generate displayedAs if blank
            if (displayedAs.isBlank()) {
                var stars = Db.instance.SELECT.FROM(Stars.class).WHERE("id = ?", starsId).EXEC();
                displayedAs = stars.isEmpty() ? "rating" : stars.get(0).displayedAs + " rating";
            }

            Rating rating = new Rating();
            rating.displayedAs = displayedAs;
            rating.starsId     = starsId;
            rating.userId      = userId;
            rating.artworkId   = artworkId;
            Db.instance.INSERT(rating).EXEC();

            exchange.redirect("/ratings");
        } catch (Exception e) {
            exchange.send(500, "Failed to create rating: " + e.getMessage());
        }
    }

    public static void delete(Exchange exchange) throws IOException {
        try {
            String idStr = exchange.formParam("id", "");
            if (idStr.isBlank()) idStr = exchange.queryParam("id", "");
            if (idStr.isBlank()) { exchange.send(400, "id is required"); return; }
            Db.instance.DELETE.FROM(Rating.class).BY_ID(Integer.parseInt(idStr)).EXEC();
            exchange.redirect("/ratings");
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }
}
