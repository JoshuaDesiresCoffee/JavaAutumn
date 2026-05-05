package Implementation.handler;

import Autumn.handler.BaseHandler;
import Autumn.handler.CrudHandler;
import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.Templater;
import Implementation.repository.Artist;
import Implementation.repository.Artwork;
import Implementation.repository.Provenance;
import Implementation.repository.Stars;
import Implementation.repository.User;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArtworkHandler extends CrudHandler<Artwork> {

    public ArtworkHandler() {
        super(Artwork.class, "/artworks", "artworks.html", "artworks");
    }

    @Override
    protected void decorateRow(Artwork artwork, Map<String, Object> row) {
        Map<Integer, String> artistNames = artistMap.get();
        Map<Integer, String> provenanceNames = provenanceMap.get();
        row.put("artist",     artistNames.getOrDefault(artwork.artistId, ""));
        row.put("provenance", provenanceNames.getOrDefault(artwork.provenanceId, ""));
    }

    @Override
    protected Map<String, Object> extraListContext() {
        return Map.of(
            "users",       BaseHandler.selectAllRows(User.class),
            "artists",     BaseHandler.selectAllRows(Artist.class),
            "provenances", BaseHandler.selectAllRows(Provenance.class),
            "starsList",   BaseHandler.selectAllRows(Stars.class)
        );
    }

    /** Cache the artist/provenance lookup tables for one list() call. */
    private final ThreadLocal<Map<Integer, String>> artistMap = ThreadLocal.withInitial(this::loadArtistNames);
    private final ThreadLocal<Map<Integer, String>> provenanceMap = ThreadLocal.withInitial(this::loadProvenanceNames);

    @Override
    public void list(Exchange exchange) throws IOException {
        try {
            artistMap.set(loadArtistNames());
            provenanceMap.set(loadProvenanceNames());
            super.list(exchange);
        } finally {
            artistMap.remove();
            provenanceMap.remove();
        }
    }

    private Map<Integer, String> loadArtistNames() {
        Map<Integer, String> names = new HashMap<>();
        for (Artist a : Db.instance.SELECT.FROM(Artist.class).EXEC()) names.put(a.id, a.displayedAs);
        return names;
    }

    private Map<Integer, String> loadProvenanceNames() {
        Map<Integer, String> names = new HashMap<>();
        for (Provenance p : Db.instance.SELECT.FROM(Provenance.class).EXEC()) names.put(p.id, p.displayedAs);
        return names;
    }

    @Override
    protected Artwork bindFromForm(Exchange exchange, boolean includeId) throws Exception {
        Artwork artwork = super.bindFromForm(exchange, includeId);
        if (artwork.pictureUrl == null) artwork.pictureUrl = "";
        return artwork;
    }

    @Override
    protected String validate(Artwork artwork) {
        if (artwork.displayedAs == null || artwork.displayedAs.isBlank()) {
            return "Title, artist and provenance are required";
        }
        if (artwork.artistId == 0 || artwork.provenanceId == 0) {
            return "Title, artist and provenance are required";
        }
        return null;
    }

    public void editForm(Exchange exchange) throws IOException {
        try {
            String idStr = exchange.queryParam("id", "");
            if (idStr.isBlank()) {
                exchange.send(400, "id is required");
                return;
            }
            int id = Integer.parseInt(idStr);
            List<Artwork> artworks = Db.instance.SELECT.FROM(Artwork.class).WHERE("id = ?", id).EXEC();
            if (artworks.isEmpty()) {
                exchange.send(404, "Artwork not found");
                return;
            }
            Artwork artwork = artworks.get(0);

            Map<String, Object> model = new HashMap<>();
            model.put("artworkId",            artwork.id);
            model.put("artworkTitle",         artwork.displayedAs);
            model.put("artworkMaterial",      artwork.material);
            model.put("artworkArtistId",      artwork.artistId);
            model.put("artworkProvenanceId",  artwork.provenanceId);
            model.put("artists",              BaseHandler.selectAllRows(Artist.class));
            model.put("provenances",          BaseHandler.selectAllRows(Provenance.class));

            exchange.html(Templater.render("artwork_edit.html", model));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }
}
