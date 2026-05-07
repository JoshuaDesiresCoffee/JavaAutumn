package Implementation;

import Autumn.Router;
import Autumn.handler.BaseHandler;
import Autumn.handler.StaticAssetHandler;
import Autumn.orm.Db;
import Implementation.handler.*;
import Implementation.repository.Artist;
import Implementation.repository.Artwork;
import Implementation.repository.Epoch;
import Implementation.repository.Provenance;
import Implementation.repository.Rating;
import Implementation.repository.Role;
import Implementation.repository.Stars;
import Implementation.repository.User;

public class App {

    public static final int PORT = 8080;

    public static void main(String[] args) {

        Db.from(AppConfig.class).sync(SeedDatabase.TABLES);

        var router = new Router(PORT);

        router.GET("/static/", StaticAssetHandler.withRoot("/static/", "Implementation/static"));

        router.GET("/",      IndexHandler::get);
        router.GET("/users", IndexHandler::listUsers);

        var userApi = new UserAPIHandler();
        router.GET("/api/user/all",    userApi::list);
        router.POST("/api/user_create", userApi::create);
        router.POST("/api/user_update", userApi::update);
        router.POST("/api/user_delete", userApi::delete);

        router.GET("/api/sidebar", SidebarAPIHandler::getSidebarData);

        router.GET("/artist",     ex -> BaseHandler.renderDetail(ex, "Artist", Artist.class));
        router.GET("/artwork",    ex -> BaseHandler.renderDetail(ex, "Artwork", Artwork.class));
        router.GET("/provenance", ex -> BaseHandler.renderDetail(ex, "Provenance", Provenance.class));
        router.GET("/epoch",      ex -> BaseHandler.renderDetail(ex, "Epoch", Epoch.class));
        router.GET("/user",       ex -> BaseHandler.renderDetail(ex, "User", User.class));
        router.GET("/role",       ex -> BaseHandler.renderDetail(ex, "Role", Role.class));
        router.GET("/rating",     ex -> BaseHandler.renderDetail(ex, "Rating", Rating.class));
        router.GET("/stars",      ex -> BaseHandler.renderDetail(ex, "Stars", Stars.class));

        var artworks = new ArtworkHandler();
        artworks.registerCrud(router);
        router.GET("/artworks/edit",   artworks::editForm);
        router.GET("/api/artwork/all", artworks::api);

        var ratings = new RatingHandler();
        router.GET("/ratings",         ratings::list);
        router.POST("/ratings/create", ratings::create);
        router.POST("/ratings/delete", ratings::delete);

        var stars = new StarsHandler();
        router.GET("/stars",         stars::list);
        router.POST("/stars/create", stars::create);
        router.POST("/stars/delete", stars::delete);

        var roles = new RoleHandler();
        router.GET("/roles",              roles::list);
        router.POST("/roles/create",      roles::create);
        router.POST("/roles/delete",      roles::delete);
        router.POST("/roles/assign",      roles::assignUser);
        router.POST("/roles/remove-user", roles::removeUser);

        router.serve();
    }
}
