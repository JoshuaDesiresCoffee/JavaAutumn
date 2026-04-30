package Implementation.handler;

import Autumn.handler.Exchange;
import Autumn.orm.Db;
import Autumn.templating.Json;
import Implementation.repository.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SidebarAPIHandler {
    public static class SidebarData {
        public java.util.List<Artist> artists;
        public java.util.List<Artwork> artworks;
        public java.util.List<Provenance> provenances;
        public java.util.List<Epoch> epochs;
        public java.util.List<User> users;
        public java.util.List<Role> roles;
        public java.util.List<Rating> ratings;
        public java.util.List<Stars> stars;
    }

    public static void getSidebarData(Exchange exchange) throws IOException {
        try {
            SidebarData data = new SidebarData();
            data.artists = Db.instance.SELECT.FROM(Artist.class).EXEC();
            data.artworks = Db.instance.SELECT.FROM(Artwork.class).EXEC();
            data.provenances = Db.instance.SELECT.FROM(Provenance.class).EXEC();
            data.epochs = Db.instance.SELECT.FROM(Epoch.class).EXEC();
            data.users = Db.instance.SELECT.FROM(User.class).EXEC();
            data.roles = Db.instance.SELECT.FROM(Role.class).EXEC();
            data.ratings = Db.instance.SELECT.FROM(Rating.class).EXEC();
            data.stars = Db.instance.SELECT.FROM(Stars.class).EXEC();
            
            exchange.json(Json.toJson(data));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }
}
