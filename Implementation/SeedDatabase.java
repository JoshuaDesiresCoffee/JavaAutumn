package Implementation;

import Autumn.orm.Db;
import Implementation.repository.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Clears and/or fills the demo SQLite database. Intended to be run explicitly
 * (CLI or script), not from {@link App} startup — {@link App} only syncs schema.
 *
 * <p>Examples:
 * <pre>{@code
 * java -cp "out;Autumn/lib/sqlite-jdbc-*.jar" Implementation.SeedDatabase
 * java -cp "..." Implementation.SeedDatabase --reset
 * }</pre>
 */
public final class SeedDatabase {

    private static final String DATABASE_PATH = "Implementation/data/app.db";
    private static final String DATABASE_URL = "jdbc:sqlite:" + DATABASE_PATH;

    public static final Class<?>[] TABLES = {
            Stars.class,
            Role.class,
            User.class,
            UserRole.class,
            Artist.class,
            Provenance.class,
            Epoch.class,
            Artwork.class,
            ArtistEpoch.class,
            Rating.class
    };

    private SeedDatabase() {}

    public static void main(String[] args) throws IOException {
        if (Arrays.asList(args).contains("--reset")) {
            reset();
            System.out.println("Database reset and seeded.");
            return;
        }
        syncSchema();
        populateIfEmpty();
        System.out.println("Database ready (schema synced; seeded only if empty).");
    }

    /** CREATE TABLE for all {@link #TABLES} entities. */
    public static void syncSchema() {
        Db.configure().url(DATABASE_URL).tables(TABLES).connect();
    }

    /** Deletes the SQLite file. Next {@link #syncSchema()} recreates empty tables. */
    public static void clearDatabaseFile() throws IOException {
        Files.deleteIfExists(Path.of(DATABASE_PATH));
    }

    /** {@link #clearDatabaseFile()}, then schema + full demo data. */
    public static void reset() throws IOException {
        clearDatabaseFile();
        syncSchema();
        insertDemoData();
    }

    /**
     * Inserts demo rows only when the DB looks uninitialized (no {@link Stars} row).
     */
    public static void populateIfEmpty() {
        if (!Db.instance.SELECT.FROM(Stars.class).LIMIT(1).EXEC().isEmpty()) {
            return;
        }
        insertDemoData();
    }

    private static void insertDemoData() {
        // ── Stars (1-5 levels) ────────────────────────────────────────────
        String[] symbols = {"•", "••", "•••", "••••", "•••••"};
        Stars[] starsArr = new Stars[5];
        for (int i = 0; i < 5; i++) {
            Stars s = new Stars();
            s.value = i + 1;
            s.displayedAs = symbols[i];
            Db.instance.INSERT.INTO(Stars.class).VALUES(s).EXEC();
            starsArr[i] = s;
        }

        // ── Roles ─────────────────────────────────────────────────────────
        Role guest = new Role();
        guest.displayedAs = "GUEST";
        Db.instance.INSERT.INTO(Role.class).VALUES(guest).EXEC();
        Role auth = new Role();
        auth.displayedAs = "AUTH";
        Db.instance.INSERT.INTO(Role.class).VALUES(auth).EXEC();
        Role admin = new Role();
        admin.displayedAs = "ADMIN";
        Db.instance.INSERT.INTO(Role.class).VALUES(admin).EXEC();

        // ── Users ─────────────────────────────────────────────────────────
        User freddy = new User();
        freddy.name = "Freddy Mercury";
        freddy.email = "freddy@example.test";
        Db.instance.INSERT.INTO(User.class).VALUES(freddy).EXEC();
        User udo = new User();
        udo.name = "Udo Lindenberg";
        udo.email = "udo@example.test";
        Db.instance.INSERT.INTO(User.class).VALUES(udo).EXEC();
        User alex = new User();
        alex.name = "Alexander Frege";
        alex.email = "alex@example.test";
        Db.instance.INSERT.INTO(User.class).VALUES(alex).EXEC();

        // ── UserRoles ─────────────────────────────────────────────────────
        userRole(freddy, guest);
        userRole(udo, auth);
        userRole(alex, admin);

        // ── Provenances ───────────────────────────────────────────────────
        Provenance louvre = prov("Louvre");
        Provenance musee = prov("Musée D'Orsay");
        Provenance landesmus = prov("Landesmuseum CH");
        Provenance albertina = prov("Albertina");

        // ── Epochs ────────────────────────────────────────────────────────
        Epoch classic = epoch("Classic");
        Epoch gothic = epoch("Gothic");
        Epoch renaissance = epoch("Renaissance");
        Epoch baroque = epoch("Baroque");
        Epoch impressionism = epoch("Impressionism");

        // ── Artists ───────────────────────────────────────────────────────
        Artist botticelli = artist("Botticelli",
                "Sandro di Mariano di Vanni Filipepi, gen. Botticelli",
                "01.03.1445", "17.05.1510",
                "https://de.wikipedia.org/wiki/Sandro_Botticelli",
                "https://www.van-ham.com/fileadmin/Ads/Sandro_Botti.jpg");

        Artist leonardo = artist("Leonardo",
                "Leonardo di ser Piero da Vinci",
                "15.04.1452", "02.05.1519",
                "https://de.wikipedia.org/wiki/Leonardo_da_Vinci",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/b/ba/Leonardo_self.jpg/402px-Leonardo_self.jpg");

        Artist velazquez = artist("Velázquez",
                "Diego Rodríguez de Silva y Velázquez",
                "06.06.1599", "06.08.1660",
                "https://de.wikipedia.org/wiki/Diego_Vel%C3%A1zquez",
                "");

        Artist rembrandt = artist("Rembrandt",
                "Rembrandt Harmenszoon van Rijn",
                "15.07.1606", "04.10.1669",
                "https://de.wikipedia.org/wiki/Rembrandt_van_Rijn",
                "");

        Artist caravaggio = artist("Caravaggio",
                "Michelangelo Merisi da Caravaggio",
                "29.09.1571", "18.07.1610",
                "https://de.wikipedia.org/wiki/Caravaggio",
                "");

        // ── ArtistEpochs ──────────────────────────────────────────────────
        artistEpoch(botticelli, gothic);
        artistEpoch(botticelli, renaissance);
        artistEpoch(leonardo, renaissance);
        artistEpoch(velazquez, baroque);
        artistEpoch(rembrandt, baroque);
        artistEpoch(caravaggio, baroque);

        // ── Artworks ──────────────────────────────────────────────────────
        Artwork birthOfVenus = artwork("Birth of Venus", "Tempera on canvas",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/2/26/Sandro_Botticelli_046.jpg/800px-Sandro_Botticelli_046.jpg",
                botticelli, louvre);
        Artwork monaLisa = artwork("Mona Lisa", "Oil on poplar wood",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ec/Mona_Lisa%2C_by_Leonardo_da_Vinci%2C_from_C2RMF_retouched.jpg/800px-Mona_Lisa%2C_by_Leonardo_da_Vinci%2C_from_C2RMF_retouched.jpg",
                leonardo, louvre);
        artwork("Primavera", "Oil on Canvas",
                "https://etsy.com/...primavera", botticelli, albertina);
        artwork("Las Meninas", "Oil on canvas",
                "", velazquez, landesmus);
        artwork("The Night's Watch", "Oil on canvas",
                "", rembrandt, landesmus);
        artwork("Mary Magdalene", "Oil on canvas",
                "", caravaggio, musee);

        // ── Ratings ───────────────────────────────────────────────────────
        rating("first rating",  birthOfVenus, starsArr[4], udo);
        rating("second rating", monaLisa,     starsArr[3], freddy);
    }

    private static Provenance prov(String name) {
        Provenance p = new Provenance();
        p.displayedAs = name;
        Db.instance.INSERT.INTO(Provenance.class).VALUES(p).EXEC();
        return p;
    }

    private static Epoch epoch(String name) {
        Epoch e = new Epoch();
        e.displayedAs = name;
        Db.instance.INSERT.INTO(Epoch.class).VALUES(e).EXEC();
        return e;
    }

    private static Artist artist(String displayedAs, String fullName,
                                 String birth, String death,
                                 String bioUrl, String pictureUrl) {
        Artist a = new Artist();
        a.displayedAs = displayedAs;
        a.fullName = fullName;
        a.birthDate = birth;
        a.deathDate = death;
        a.bioUrl = bioUrl;
        a.pictureUrl = pictureUrl;
        Db.instance.INSERT.INTO(Artist.class).VALUES(a).EXEC();
        return a;
    }

    private static Artwork artwork(String title, String material,
                                   String pictureUrl, Artist artist, Provenance provenance) {
        Artwork a = new Artwork();
        a.displayedAs = title;
        a.material = material;
        a.pictureUrl = pictureUrl;
        a.artist = artist;
        a.provenance = provenance;
        Db.instance.INSERT.INTO(Artwork.class).VALUES(a).EXEC();
        return a;
    }

    private static void artistEpoch(Artist artist, Epoch epoch) {
        ArtistEpoch ae = new ArtistEpoch();
        ae.artist = artist;
        ae.epoch = epoch;
        Db.instance.INSERT.INTO(ArtistEpoch.class).VALUES(ae).EXEC();
    }

    private static void userRole(User user, Role role) {
        UserRole ur = new UserRole();
        ur.user = user;
        ur.role = role;
        Db.instance.INSERT.INTO(UserRole.class).VALUES(ur).EXEC();
    }

    private static void rating(String label, Artwork artwork, Stars stars, User user) {
        Rating r = new Rating();
        r.displayedAs = label;
        r.artwork = artwork;
        r.stars = stars;
        r.user = user;
        Db.instance.INSERT.INTO(Rating.class).VALUES(r).EXEC();
    }
}
