package Implementation.handler;

import Autumn.handler.BaseHandler;
import Autumn.handler.CrudHandler;
import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.ObjectToMapConverter;
import Autumn.templating.Templater;
import Implementation.repository.Artwork;
import Implementation.repository.Rating;
import Implementation.repository.Stars;
import Implementation.repository.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RatingHandler extends CrudHandler<Rating> {

    public RatingHandler() {
        super(Rating.class, "/ratings", "ratings.html", "ratings");
    }

    /** Joins ratings with artwork/user/stars labels, so we render manually. */
    @Override
    public void list(Exchange exchange) throws IOException {
        try {
            List<Rating> ratings   = Db.instance.SELECT.FROM(Rating.class).EXEC();
            List<Artwork> artworks = Db.instance.SELECT.FROM(Artwork.class).EXEC();
            List<User> users       = Db.instance.SELECT.FROM(User.class).EXEC();
            List<Stars> starsList  = Db.instance.SELECT.FROM(Stars.class).EXEC();

            Map<Integer, String> artworkNames = new HashMap<>();
            for (Artwork a : artworks) artworkNames.put(a.id, a.displayedAs);
            Map<Integer, String> userNames = new HashMap<>();
            for (User u : users) userNames.put(u.id, u.name);
            Map<Integer, String> starsLabels = new HashMap<>();
            for (Stars s : starsList) starsLabels.put(s.id, s.displayedAs);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Rating r : ratings) {
                Map<String, Object> row = ObjectToMapConverter.convert(r);
                if (row == null) continue;
                row.put("artworkName", artworkNames.getOrDefault(r.artworkId, ""));
                row.put("userName",    userNames.getOrDefault(r.userId, ""));
                row.put("starsLabel",  starsLabels.getOrDefault(r.starsId, ""));
                rows.add(row);
            }

            exchange.html(Templater.render(listTemplate, Map.of(
                    "ratings",   rows,
                    "artworks",  BaseHandler.toRows(artworks),
                    "users",     BaseHandler.toRows(users),
                    "starsList", BaseHandler.toRows(starsList)
            )));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    @Override
    protected Rating bindFromForm(Exchange exchange, boolean includeId) throws Exception {
        Rating rating = super.bindFromForm(exchange, includeId);
        if (rating.displayedAs == null || rating.displayedAs.isBlank()) {
            List<Stars> stars = Db.instance.SELECT.FROM(Stars.class).WHERE("id = ?", rating.starsId).EXEC();
            rating.displayedAs = stars.isEmpty() ? "rating" : stars.get(0).displayedAs + " rating";
        }
        return rating;
    }

    @Override
    protected String validate(Rating rating) {
        if (rating.artworkId == 0) return "artworkId is required";
        if (rating.userId == 0)    return "userId is required";
        if (rating.starsId == 0)   return "starsId is required";
        for (Rating existing : Db.instance.SELECT.FROM(Rating.class).EXEC()) {
            if (existing.userId == rating.userId && existing.artworkId == rating.artworkId) {
                return "Error: User has already rated this artwork.";
            }
        }
        return null;
    }
}
