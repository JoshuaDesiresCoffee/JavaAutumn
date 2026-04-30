package Implementation;

import Autumn.Router;
import Autumn.handler.StaticAssetHandler;
import Implementation.handler.*;

public class App {

    public static final int PORT = 8080;

    public static void main(String[] args) {

        SampleData.syncSchema();

        var router = new Router(PORT);

        router.GET("/static/", StaticAssetHandler.withRoot("/static/", "Implementation/static"));

        // Home & Users
        router.GET("/",           IndexHandler::get);
        router.GET("/users",      IndexHandler::listUsers);
        router.GET("/api/user/all",    UserAPIHandler::list);
        router.GET("/api/user_create", UserAPIHandler::create);
        router.GET("/api/user_update", UserAPIHandler::update);
        router.GET("/api/user_delete", UserAPIHandler::delete);
        
        // Sidebar API
        router.GET("/api/sidebar", SidebarAPIHandler::getSidebarData);

        // Entity Detail Pages
        router.GET("/artist",     ex -> EntityDetailHandler.render(ex, "Artist", Implementation.repository.Artist.class));
        router.GET("/artwork",    ex -> EntityDetailHandler.render(ex, "Artwork", Implementation.repository.Artwork.class));
        router.GET("/provenance", ex -> EntityDetailHandler.render(ex, "Provenance", Implementation.repository.Provenance.class));
        router.GET("/epoch",      ex -> EntityDetailHandler.render(ex, "Epoch", Implementation.repository.Epoch.class));
        router.GET("/user",       ex -> EntityDetailHandler.render(ex, "User", Implementation.repository.User.class));
        router.GET("/role",       ex -> EntityDetailHandler.render(ex, "Role", Implementation.repository.Role.class));
        router.GET("/rating",     ex -> EntityDetailHandler.render(ex, "Rating", Implementation.repository.Rating.class));
        router.GET("/stars",      ex -> EntityDetailHandler.render(ex, "Stars", Implementation.repository.Stars.class));

        // Artworks
        router.GET("/artworks",           ArtworkHandler::list);
        router.GET("/artworks/edit",      ArtworkHandler::editForm);
        router.POST("/artworks/create",   ArtworkHandler::create);
        router.POST("/artworks/update",   ArtworkHandler::update);
        router.POST("/artworks/delete",   ArtworkHandler::delete);
        router.GET("/api/artwork/all",    ArtworkHandler::api);

        // Ratings
        router.GET("/ratings",            RatingHandler::list);
        router.POST("/ratings/create",    RatingHandler::create);
        router.POST("/ratings/delete",    RatingHandler::delete);

        // Stars
        router.GET("/stars",              StarsHandler::list);
        router.POST("/stars/create",      StarsHandler::create);
        router.POST("/stars/delete",      StarsHandler::delete);

        // Roles
        router.GET("/roles",              RoleHandler::list);
        router.POST("/roles/create",      RoleHandler::create);
        router.POST("/roles/delete",      RoleHandler::delete);
        router.POST("/roles/assign",      RoleHandler::assignUser);
        router.POST("/roles/remove-user", RoleHandler::removeUser);

        SampleData.ensure();

        router.serve();
    }
}

