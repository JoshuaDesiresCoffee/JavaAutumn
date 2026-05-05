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

    @Override
    protected void decorateRow(Stars stars, Map<String, Object> row) {
        List<Rating> ratings = Db.instance.SELECT.FROM(Rating.class).EXEC();
        long ratingCount = ratings.stream().filter(r -> r.starsId == stars.id).count();
        row.put("ratingCount", ratingCount);
    }

    @Override
    protected String validate(Stars stars) {
        if (stars.displayedAs == null || stars.displayedAs.isBlank()) return "displayedAs is required";
        if (stars.value == 0) return "value is required";
        return null;
    }
}
