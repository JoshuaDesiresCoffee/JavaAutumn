package Implementation.handler;

import Autumn.handler.BaseHandler;
import Autumn.handler.CrudHandler;
import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Implementation.repository.Artist;
import Implementation.repository.ArtistEpoch;
import Implementation.repository.Artwork;
import Implementation.repository.Epoch;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ArtistHandler extends CrudHandler<Artist> {

    public ArtistHandler() {
        super(Artist.class, "/artists", "artists.html", "artists");
    }

    @Override
    protected String validate(Artist a) {
        if (a.displayedAs == null || a.displayedAs.isBlank()) return "Artist name is required";
        return null;
    }

    // returns 200 OK so sidebar fetch() can handle response
    public void createFromSidebar(Exchange exchange) throws IOException {
        try {
            Artist a = new Artist();
            a.displayedAs = exchange.formParam("displayedAs", "");
            a.fullName    = exchange.formParam("fullName",    "");
            a.birthDate   = exchange.formParam("birthDate",   "");
            a.deathDate   = exchange.formParam("deathDate",   "");
            a.bioUrl      = exchange.formParam("bioUrl",      "");
            a.pictureUrl  = exchange.formParam("pictureUrl",  "");
            String err = validate(a);
            if (err != null) { exchange.send(400, err); return; }
            Db.instance.INSERT.INTO(Artist.class).VALUES(a).EXEC();
            // return the new id so JS can track ownership in localStorage
            List<Artist> created = Db.instance.SELECT.FROM(Artist.class)
                    .WHERE("displayedAs = ?", a.displayedAs).EXEC();
            int newId = created.isEmpty() ? -1 : created.get(created.size() - 1).id;
            exchange.send(200, String.valueOf(newId));
        } catch (Exception e) {
            exchange.send(500, "Failed to create Artist: " + e.getMessage());
        }
    }

    // edit form page
    public void editForm(Exchange exchange) throws IOException {
        Optional<Integer> idOpt = BaseHandler.intFormParam(exchange, "id");
        if (idOpt.isEmpty()) { exchange.send(400, "id is required"); return; }
        try {
            List<Artist> hits = Db.instance.SELECT.FROM(Artist.class)
                    .WHERE("id = ?", idOpt.get()).LIMIT(1).EXEC();
            if (hits.isEmpty()) { exchange.send(404, "Artist not found"); return; }
            exchange.html(Autumn.templating.Templater.render("artist_edit.html", Map.of("artist", hits.get(0))));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    // assign an epoch to an artist
    public void assignEpoch(Exchange exchange) throws IOException {
        Optional<Integer> artistId = BaseHandler.intFormParam(exchange, "artistId");
        Optional<Integer> epochId  = BaseHandler.intFormParam(exchange, "epochId");
        if (artistId.isEmpty() || epochId.isEmpty()) {
            exchange.send(400, "artistId and epochId are required");
            return;
        }
        try {
            ArtistEpoch ae = new ArtistEpoch();
            ae.artist = Db.instance.stub(Artist.class, artistId.get());
            ae.epoch  = Db.instance.stub(Epoch.class,  epochId.get());
            Db.instance.INSERT.INTO(ArtistEpoch.class).VALUES(ae).EXEC();
            exchange.send(200, "OK");
        } catch (Exception e) {
            exchange.send(500, "Failed to assign epoch: " + e.getMessage());
        }
    }
}
