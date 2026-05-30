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
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ArtworkHandler extends CrudHandler<Artwork> {

    public ArtworkHandler() {
        super(Artwork.class, "/artworks", "artworks.html", "artworks");
    }

    /** Eager-load artist and provenance so templates can reach {@code {{ artist.displayedAs }}}. */
    @Override
    protected List<Artwork> selectAll() {
        return Db.instance.SELECT.FROM(Artwork.class)
                .JOIN(Artist.class)
                .JOIN(Provenance.class)
                .EXEC();
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

    @Override
    protected String validate(Artwork artwork) {
        if (artwork.displayedAs == null || artwork.displayedAs.isBlank()
                || artwork.artist == null || artwork.provenance == null) {
            return "Title, artist and provenance are required";
        }
        return null;
    }

    public void editForm(Exchange exchange) throws IOException {
        Optional<Integer> idOpt = BaseHandler.intFormParam(exchange, "id");
        if (idOpt.isEmpty()) {
            exchange.send(400, "id is required");
            return;
        }
        try {
            List<Artwork> artworks = Db.instance.SELECT.FROM(Artwork.class)
                    .JOIN(Artist.class).JOIN(Provenance.class)
                    .WHERE("id = ?", idOpt.get()).LIMIT(1).EXEC();
            if (artworks.isEmpty()) {
                exchange.send(404, "Artwork not found");
                return;
            }
            exchange.html(Templater.render("artwork_edit.html", Map.of(
                    "artwork",     artworks.get(0),
                    "artists",     BaseHandler.selectAllRows(Artist.class),
                    "provenances", BaseHandler.selectAllRows(Provenance.class)
            )));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    // used by sidebar inline form - sends 200 instead of redirect so fetch() works
    public void createFromSidebar(Exchange exchange) throws IOException {
        try {
            Artwork entity = bindFromForm(exchange, false);
            String error = validate(entity);
            if (error != null) { exchange.send(400, error); return; }
            Db.instance.INSERT.INTO(Artwork.class).VALUES(entity).EXEC();
            exchange.send(200, "OK");
        } catch (Exception e) {
            exchange.send(500, "Failed to create Artwork: " + e.getMessage());
        }
    }
}
