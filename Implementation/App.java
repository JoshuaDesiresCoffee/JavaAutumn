package Implementation;

import Autumn.Router;
import Autumn.handler.StaticAssetHandler;
import Implementation.handler.*;

public class App {

    public static final int PORT = 8080;

    public static void main(String[] args) {

        var router = new Router(PORT);

        router.GET("/static/", StaticAssetHandler.withRoot("/static/", "Implementation/static"));

        router.GET("/", IndexHandler::get);
        router.GET("/users", IndexHandler::listUsers);

        router.GET("/api/user/all", UserAPIHandler::list);
        router.GET("/api/user_create", UserAPIHandler::create);
        router.GET("/api/user_update", UserAPIHandler::update);
        router.GET("/api/user_delete", UserAPIHandler::delete);

        router.serve();
    }
}