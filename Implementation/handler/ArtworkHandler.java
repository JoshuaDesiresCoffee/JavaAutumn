package Implementation.handler;

import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.Json;
import Autumn.templating.ObjectToMapConverter;
import Autumn.templating.Templater;
import Implementation.repository.Artist;
import Implementation.repository.Artwork;
import Implementation.repository.Provenance;
import Implementation.repository.Stars;
import Implementation.repository.User;


import java.io.IOException;
import java.util.*;

public class ArtworkHandler {
    public static void list(Exchange exchange) throws IOException {
        var artists = Db.instance.SELECT.FROM(Artist.class).EXEC();
        var provenances = Db.instance.SELECT.FROM(Provenance.class).EXEC();
        var artworks = Db.instance.SELECT.FROM(Artwork.class).EXEC();
        var users = Db.instance.SELECT.FROM(User.class).EXEC();
        var stars = Db.instance.SELECT.FROM(Stars.class).EXEC();

        Map<Integer, String> artistNames = new HashMap<>();
        for (Artist artist : artists) {
            artistNames.put(artist.id, artist.displayedAs);
        }

        Map<Integer, String> provenanceNames = new HashMap<>();
        for (Provenance provenance : provenances) {
            provenanceNames.put(provenance.id, provenance.displayedAs);
        }

        List<Map<String, Object>> rows = new ArrayList<>(artworks.size());
        for (Artwork artwork : artworks) {
            Map<String, Object> row = ObjectToMapConverter.convert(artwork);
            if (row == null) continue;
            row.put("artist", artistNames.getOrDefault(artwork.artistId, ""));
            row.put("provenance", provenanceNames.getOrDefault(artwork.provenanceId, ""));
            rows.add(row);
        }

        List<Map<String, Object>> userRows = new ArrayList<>(users.size());
        for (User user : users) {
            Map<String, Object> uRow = ObjectToMapConverter.convert(user);
            if (uRow != null) userRows.add(uRow);
        }

        List<Map<String, Object>> starsRows = new ArrayList<>(stars.size());
        for (Stars s : stars) {
            Map<String, Object> sRow = ObjectToMapConverter.convert(s);
            if (sRow != null) starsRows.add(sRow);
        }

        List<Map<String, Object>> artistRows = new ArrayList<>();
        for (Artist a : artists) {
            Map<String, Object> r = ObjectToMapConverter.convert(a);
            if (r != null) artistRows.add(r);
        }

        List<Map<String, Object>> provRows = new ArrayList<>();
        for (Provenance p : provenances) {
            Map<String, Object> r = ObjectToMapConverter.convert(p);
            if (r != null) provRows.add(r);
        }

        exchange.html(Templater.render("artworks.html", Map.of(
            "artworks", rows,
            "users", userRows,
            "artists", artistRows,
            "provenances", provRows,
            "starsList", starsRows
        )));
    }

    public static void api(Exchange exchange) throws IOException {
        exchange.json(Json.toJson(Db.instance.SELECT.FROM(Artwork.class).EXEC()));
    }



    public static void create(Exchange exchange) throws IOException {
        try {
            String title = exchange.formParam("displayedAs", "");
            String material = exchange.formParam("material", "");
            String artistIdStr = exchange.formParam("artistId", "");
            String provenanceIdStr = exchange.formParam("provenanceId", "");

            if (title.isBlank() || artistIdStr.isBlank() || provenanceIdStr.isBlank()) {
                exchange.send(400, "Title, artist and provenance are required");
                return;
            }

            Artwork a = new Artwork();
            a.displayedAs = title;
            a.material = material;
            a.pictureUrl = "";
            a.artistId = Integer.parseInt(artistIdStr);
            a.provenanceId = Integer.parseInt(provenanceIdStr);
            
            Db.instance.INSERT(a).EXEC();
            exchange.redirect("/artworks");
        } catch (Exception e) {
            exchange.send(500, "Failed to create artwork: " + e.getMessage());
        }
    }

    public static void delete(Exchange exchange) throws IOException {
        try {
            String idStr = exchange.formParam("id", "");
            if (idStr.isBlank()) {
                idStr = exchange.queryParam("id", "");
            }
            if (idStr.isBlank()) {
                exchange.send(400, "id is required");
                return;
            }

            Db.instance.DELETE.FROM(Artwork.class).BY_ID(Integer.parseInt(idStr)).EXEC();
            exchange.redirect("/artworks");
        } catch (Exception e) {
            String msg = e.getMessage();
            if (e.getCause() != null) {
                msg += " " + e.getCause().getMessage();
            }
            if (msg.contains("FOREIGN KEY constraint failed")) {
                exchange.html(Autumn.templating.Templater.render("error.html", java.util.Map.of("errorMessage", "Cannot delete Artwork because it is still referenced by other records (e.g., Ratings).")));
            } else {
                exchange.html(Autumn.templating.Templater.render("error.html", java.util.Map.of("errorMessage", e.getMessage())));
            }
        }
    }

    public static void editForm(Exchange exchange) throws IOException {
        try {
            String idStr = exchange.queryParam("id", "");
            if (idStr.isBlank()) {
                exchange.send(400, "id is required");
                return;
            }
            int id = Integer.parseInt(idStr);
            var artworks = Db.instance.SELECT.FROM(Artwork.class).WHERE("id = ?", id).EXEC();
            if (artworks.isEmpty()) {
                exchange.send(404, "Artwork not found");
                return;
            }
            Artwork a = artworks.get(0);
            var artists = Db.instance.SELECT.FROM(Artist.class).EXEC();
            var provenances = Db.instance.SELECT.FROM(Provenance.class).EXEC();

            List<Map<String, Object>> artistRows = new ArrayList<>();
            for (Artist artist : artists) {
                Map<String, Object> r = ObjectToMapConverter.convert(artist);
                if (r != null) artistRows.add(r);
            }

            List<Map<String, Object>> provRows = new ArrayList<>();
            for (Provenance p : provenances) {
                Map<String, Object> r = ObjectToMapConverter.convert(p);
                if (r != null) provRows.add(r);
            }

            Map<String, Object> model = new java.util.HashMap<>();
            model.put("artworkId", a.id);
            model.put("artworkTitle", a.displayedAs);
            model.put("artworkMaterial", a.material);
            model.put("artworkArtistId", a.artistId);
            model.put("artworkProvenanceId", a.provenanceId);
            model.put("artists", artistRows);
            model.put("provenances", provRows);

            exchange.html(Templater.render("artwork_edit.html", model));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    public static void update(Exchange exchange) throws IOException {
        try {
            String idStr = exchange.formParam("id", "");
            String title = exchange.formParam("displayedAs", "");
            String material = exchange.formParam("material", "");
            String artistIdStr = exchange.formParam("artistId", "");
            String provenanceIdStr = exchange.formParam("provenanceId", "");

            if (idStr.isBlank() || title.isBlank() || artistIdStr.isBlank() || provenanceIdStr.isBlank()) {
                exchange.send(400, "All fields are required");
                return;
            }

            Artwork a = new Artwork();
            a.id = Integer.parseInt(idStr);
            a.displayedAs = title;
            a.material = material;
            a.pictureUrl = "";
            a.artistId = Integer.parseInt(artistIdStr);
            a.provenanceId = Integer.parseInt(provenanceIdStr);
            
            Db.instance.UPDATE(a).BY_ID(a.id).EXEC();
            exchange.redirect("/artworks");
        } catch (Exception e) {
            exchange.send(500, "Failed to update artwork: " + e.getMessage());
        }
    }
}
