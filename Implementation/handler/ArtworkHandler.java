package Implementation.handler;

import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.Json;
import Autumn.templating.ObjectToMapConverter;
import Autumn.templating.Templater;
import Implementation.repository.Artist;
import Implementation.repository.Artwork;
import Implementation.repository.Provenance;

import java.io.IOException;
import java.util.*;

public class ArtworkHandler {
    public static void list(Exchange exchange) throws IOException {
        var artists = Db.instance.SELECT.FROM(Artist.class).EXEC();
        var provenances = Db.instance.SELECT.FROM(Provenance.class).EXEC();
        var artworks = Db.instance.SELECT.FROM(Artwork.class).EXEC();

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

        exchange.html(Templater.render("artworks.html", Map.of("artworks", rows)));
    }

    public static void api(Exchange exchange) throws IOException {
        exchange.json(Json.toJson(Db.instance.SELECT.FROM(Artwork.class).EXEC()));
    }
}
