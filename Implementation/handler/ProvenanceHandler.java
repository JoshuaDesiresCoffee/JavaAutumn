package Implementation.handler;

import Autumn.handler.BaseHandler;
import Autumn.handler.CrudHandler;
import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Implementation.repository.Provenance;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProvenanceHandler extends CrudHandler<Provenance> {

    public ProvenanceHandler() {
        super(Provenance.class, "/provenances", "provenances.html", "provenances");
    }

    @Override
    protected String validate(Provenance p) {
        if (p.displayedAs == null || p.displayedAs.isBlank()) return "Name is required";
        return null;
    }

    // returns new id as text so sidebar JS can track ownership
    public void createFromSidebar(Exchange exchange) throws IOException {
        try {
            Provenance p = new Provenance();
            p.displayedAs = exchange.formParam("displayedAs", "");
            String err = validate(p);
            if (err != null) { exchange.send(400, err); return; }
            Db.instance.INSERT.INTO(Provenance.class).VALUES(p).EXEC();
            List<Provenance> created = Db.instance.SELECT.FROM(Provenance.class)
                    .WHERE("displayedAs = ?", p.displayedAs).EXEC();
            int newId = created.isEmpty() ? -1 : created.get(created.size() - 1).id;
            exchange.send(200, String.valueOf(newId));
        } catch (Exception e) {
            exchange.send(500, "Failed to create Provenance: " + e.getMessage());
        }
    }

    public void editForm(Exchange exchange) throws IOException {
        Optional<Integer> idOpt = BaseHandler.intFormParam(exchange, "id");
        if (idOpt.isEmpty()) { exchange.send(400, "id is required"); return; }
        try {
            List<Provenance> hits = Db.instance.SELECT.FROM(Provenance.class)
                    .WHERE("id = ?", idOpt.get()).LIMIT(1).EXEC();
            if (hits.isEmpty()) { exchange.send(404, "Provenance not found"); return; }
            exchange.html(Autumn.templating.Templater.render("provenance_edit.html",
                    Map.of("provenance", hits.get(0))));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }
}
