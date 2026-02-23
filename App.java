import Handler.Index;
import Service.Router;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class App {

    public static final int port = 8080;

    public static void main(String[] args) throws Exception {

        var server = HttpServer.create(new InetSocketAddress(port), 0);
        var router = new Router(server);

        router.GET("/",     Index::get);

        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        System.out.println("listening on :" + port);
    }
}