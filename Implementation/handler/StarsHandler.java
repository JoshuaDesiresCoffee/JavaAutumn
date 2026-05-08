package Implementation.handler;

import Autumn.handler.CrudHandler;
import Autumn.orm.Db;
import Implementation.repository.Rating;
import Implementation.repository.Stars;

import java.util.List;
import java.util.Map;

public class StarsHandler extends CrudHandler<Stars> {

    public StarsHandler() {
        super(Stars.class, "/stars", "stars.html", "starsList");
    }

    /** Eager-load child ratings so {@link #decorateRow} can size them without a second query. */
    @Override
    protected List<Stars> selectAll() {
        return Db.instance.SELECT.FROM(Stars.class).JOIN(Rating.class).EXEC();
    }

    @Override
    protected void decorateRow(Stars stars, Map<String, Object> row) {
        row.put("ratingCount", stars.ratings == null ? 0 : stars.ratings.size());
    }

    @Override
    protected String validate(Stars stars) {
        if (stars.displayedAs == null || stars.displayedAs.isBlank()) return "displayedAs is required";
        if (stars.value == 0) return "value is required";
        return null;
    }
}
