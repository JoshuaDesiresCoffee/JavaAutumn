import Handler.Api;
import Handler.Index;
import Handler.StaticAssets;
import Service.Router;
import Service.db.Database;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class App {

    public static final int port = 8080;

    public static void main(String[] args) throws Exception {
        Database.initialize();

        var server = HttpServer.create(new InetSocketAddress(port), 0);
        var router = new Router(server);

        router.GET("/", Index::get);
        router.GET("/public/style.css", StaticAssets::style);

        router.GET("/api/tables", Api::tables);
        router.GET("/api/rows", Api::rows);
        router.POST("/api/rows", Api::rows);
        router.PUT("/api/rows", Api::rows);
        router.DELETE("/api/rows", Api::rows);

        router.POST("/create", Index::create);
        router.POST("/update", Index::update);
        router.POST("/delete", Index::delete);

        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        System.out.println("listening on :" + port);
    }
}