package Implementation.handler;

import Autumn.handler.BaseHandler;
import Autumn.handler.CrudHandler;
import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Implementation.repository.Artwork;
import Implementation.repository.Rating;
import Implementation.repository.Stars;
import Implementation.repository.User;

import java.util.List;
import java.util.Map;

public class RatingHandler extends CrudHandler<Rating> {

    public RatingHandler() {
        super(Rating.class, "/ratings", "ratings.html", "ratings");
    }

    /** Eager-load artwork, user and stars so the template can render their {@code displayedAs}/{@code name}. */
    @Override
    protected List<Rating> selectAll() {
        return Db.instance.SELECT.FROM(Rating.class)
                .JOIN(Artwork.class)
                .JOIN(User.class)
                .JOIN(Stars.class)
                .EXEC();
    }

    @Override
    protected Map<String, Object> extraListContext() {
        return Map.of(
                "artworks",  BaseHandler.selectAllRows(Artwork.class),
                "users",     BaseHandler.selectAllRows(User.class),
                "starsList", BaseHandler.selectAllRows(Stars.class)
        );
    }

    @Override
    protected Rating bindFromForm(Exchange exchange, boolean includeId) throws Exception {
        Rating rating = super.bindFromForm(exchange, includeId);
        if (rating.displayedAs == null || rating.displayedAs.isBlank()) {
            // Generate a placeholder label by looking up the chosen Stars row.
            if (rating.stars != null) {
                List<Stars> stars = Db.instance.SELECT.FROM(Stars.class)
                        .WHERE("id = ?", rating.stars.id).LIMIT(1).EXEC();
                rating.displayedAs = stars.isEmpty() ? "rating" : stars.get(0).displayedAs + " rating";
            } else {
                rating.displayedAs = "rating";
            }
        }
        return rating;
    }

    @Override
    protected String validate(Rating rating) {
        if (rating.artwork == null) return "artworkId is required";
        if (rating.user == null)    return "userId is required";
        if (rating.stars == null)   return "starsId is required";
        Rating probe = new Rating();
        probe.user = rating.user;
        probe.artwork = rating.artwork;
        if (!Db.instance.SELECT.FROM(Rating.class).WHERE(probe).LIMIT(1).EXEC().isEmpty()) {
            return "Error: User has already rated this artwork.";
        }
        return null;
    }
}
