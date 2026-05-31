package Implementation;

import Autumn.Router;
import Autumn.handler.BaseHandler;
import Autumn.handler.StaticAssetHandler;
import Autumn.orm.Db;
import Implementation.handler.*;
import Implementation.repository.*;

public class App {

    public static final int PORT = 8080;

    public static void main(String[] args) {

        Db.from(AppConfig.class).sync(SeedDatabase.TABLES);

        var router = new Router(PORT);

        BaseHandler.detailRenderer = DetailHandler::renderDetail;

        router.GET("/static/", StaticAssetHandler.withRoot("/static/", "Implementation/static"));

        router.GET("/", IndexHandler::get);
        router.GET("/users", IndexHandler::listUsers);

        // User API
        var userApi = new UserAPIHandler();
        router.GET("/api/user/all", userApi::list);
        router.POST("/api/user_create", userApi::create);
        router.POST("/api/user_update", userApi::update);
        router.POST("/api/user_delete", userApi::delete);

        router.GET("/api/sidebar", SidebarAPIHandler::getSidebarData);

        // Artist CRUD (full - sidebar create returns new id for ownership tracking)
        var artists = new ArtistHandler();
        artists.registerCrud(router);
        router.POST("/artists/create-sidebar", artists::createFromSidebar);
        router.GET("/artists/edit", artists::editForm);
        router.POST("/artists/assign-epoch", artists::assignEpoch);

        // Provenance CRUD
        var provenances = new ProvenanceHandler();
        provenances.registerCrud(router);
        router.POST("/provenances/create-sidebar", provenances::createFromSidebar);
        router.GET("/provenances/edit", provenances::editForm);

        // Artwork CRUD
        var artworks = new ArtworkHandler();
        artworks.registerCrud(router);
        router.POST("/artworks/create-sidebar", artworks::createFromSidebar);
        router.GET("/artworks/edit", artworks::editForm);
        router.GET("/api/artwork/all", artworks::api);

        // Ratings
        var ratings = new RatingHandler();
        router.GET("/ratings", ratings::list);
        router.POST("/ratings/create", ratings::create);
        router.POST("/ratings/delete", ratings::delete);

        // Stars
        var stars = new StarsHandler();
        router.GET("/stars", stars::list);
        router.POST("/stars/create", stars::create);
        router.POST("/stars/delete", stars::delete);

        // Roles
        var roles = new RoleHandler();
        router.GET("/roles", roles::list);
        router.POST("/roles/create", roles::create);
        router.POST("/roles/delete", roles::delete);
        router.POST("/roles/assign", roles::assignUser);
        router.POST("/roles/remove-user", roles::removeUser);

        // Detail pages
        router.GET("/artist", ex -> BaseHandler.renderDetail(ex, "Artist", Artist.class));
        router.GET("/artwork", ex -> BaseHandler.renderDetail(ex, "Artwork", Artwork.class));
        router.GET("/provenance", ex -> BaseHandler.renderDetail(ex, "Provenance", Provenance.class));
        router.GET("/epoch", ex -> BaseHandler.renderDetail(ex, "Epoch", Epoch.class));
        router.GET("/user", ex -> BaseHandler.renderDetail(ex, "User", User.class));
        router.GET("/role", ex -> BaseHandler.renderDetail(ex, "Role", Role.class));
        router.GET("/rating", ex -> BaseHandler.renderDetail(ex, "Rating", Rating.class));

        router.serve();
    }
}
