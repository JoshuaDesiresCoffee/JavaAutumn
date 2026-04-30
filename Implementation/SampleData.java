package Implementation;

import Autumn.orm.Db;
import Implementation.repository.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class SampleData {
    private static final String DATABASE_PATH = "Implementation/data/app.db";
    private static final String DATABASE_URL  = "jdbc:sqlite:" + DATABASE_PATH;

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

    private SampleData() {}

    public static void main(String[] args) throws IOException {
        if (Arrays.asList(args).contains("--reset")) {
            Files.deleteIfExists(Path.of(DATABASE_PATH));
        }
        Db.configure(DATABASE_URL);
        syncSchema();
        ensure();
        System.out.println("Sample database ready.");
    }

    public static void syncSchema() {
        Db.configure(DATABASE_URL);
        Db.instance.sync(TABLES);
    }

    public static void ensure() {
        if (!Db.instance.SELECT.FROM(Stars.class).LIMIT(1).EXEC().isEmpty()) {
            return; // already seeded
        }

        // ── Stars (1-5 levels) ────────────────────────────────────────────
        String[] symbols = { "•", "••", "•••", "••••", "•••••" };
        Stars[] starsArr = new Stars[5];
        for (int i = 0; i < 5; i++) {
            Stars s = new Stars();
            s.value       = i + 1;
            s.displayedAs = symbols[i];
            Db.instance.INSERT(s).EXEC();
            starsArr[i] = s;
        }

        // ── Roles ─────────────────────────────────────────────────────────
        Role guest = new Role(); guest.displayedAs = "GUEST"; Db.instance.INSERT(guest).EXEC();
        Role auth  = new Role(); auth.displayedAs  = "AUTH";  Db.instance.INSERT(auth).EXEC();
        Role admin = new Role(); admin.displayedAs = "ADMIN"; Db.instance.INSERT(admin).EXEC();

        // ── Users ─────────────────────────────────────────────────────────
        User freddy = new User(); freddy.name = "Freddy Mercury"; freddy.email = "freddy@example.test"; Db.instance.INSERT(freddy).EXEC();
        User udo    = new User(); udo.name    = "Udo Lindenberg"; udo.email    = "udo@example.test";    Db.instance.INSERT(udo).EXEC();
        User alex   = new User(); alex.name   = "Alexander Frege"; alex.email  = "alex@example.test";  Db.instance.INSERT(alex).EXEC();

        // ── UserRoles ─────────────────────────────────────────────────────
        UserRole ur1 = new UserRole(); ur1.userId = freddy.id; ur1.roleId = guest.id; Db.instance.INSERT(ur1).EXEC();
        UserRole ur2 = new UserRole(); ur2.userId = udo.id;    ur2.roleId = auth.id;  Db.instance.INSERT(ur2).EXEC();
        UserRole ur3 = new UserRole(); ur3.userId = alex.id;   ur3.roleId = admin.id; Db.instance.INSERT(ur3).EXEC();

        // ── Provenances ───────────────────────────────────────────────────
        Provenance louvre     = prov("Louvre");
        Provenance musee      = prov("Musée D'Orsay");
        Provenance landesmus  = prov("Landesmuseum CH");
        Provenance albertina  = prov("Albertina");

        // ── Epochs ────────────────────────────────────────────────────────
        Epoch classic      = epoch("Classic");
        Epoch gothic       = epoch("Gothic");
        Epoch renaissance  = epoch("Renaissance");
        Epoch baroque      = epoch("Baroque");
        Epoch impressionism= epoch("Impressionism");

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
        artistEpoch(botticelli.id, gothic.id);
        artistEpoch(botticelli.id, renaissance.id);
        artistEpoch(leonardo.id,   renaissance.id);
        artistEpoch(velazquez.id,  baroque.id);
        artistEpoch(rembrandt.id,  baroque.id);
        artistEpoch(caravaggio.id, baroque.id);

        // ── Artworks ──────────────────────────────────────────────────────
        Artwork primavera = artwork("Primavera", "Oil on Canvas",
            "https://etsy.com/...primavera", botticelli.id, albertina.id);
        Artwork birthOfVenus = artwork("Birth of Venus", "Tempera on canvas",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/2/26/Sandro_Botticelli_046.jpg/800px-Sandro_Botticelli_046.jpg",
            botticelli.id, louvre.id);
        Artwork monaLisa = artwork("Mona Lisa", "Oil on poplar wood",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ec/Mona_Lisa%2C_by_Leonardo_da_Vinci%2C_from_C2RMF_retouched.jpg/800px-Mona_Lisa%2C_by_Leonardo_da_Vinci%2C_from_C2RMF_retouched.jpg",
            leonardo.id, louvre.id);
        Artwork lasMeninas = artwork("Las Meninas", "Oil on canvas",
            "", velazquez.id, landesmus.id);
        Artwork nightWatch = artwork("The Night's Watch", "Oil on canvas",
            "", rembrandt.id, landesmus.id);
        Artwork maryMagdalene = artwork("Mary Magdalene", "Oil on canvas",
            "", caravaggio.id, musee.id);

        // ── Ratings ───────────────────────────────────────────────────────
        Rating r1 = new Rating();
        r1.displayedAs = "first rating";
        r1.artworkId   = birthOfVenus.id;
        r1.starsId     = starsArr[4].id; // •••••
        r1.userId      = udo.id;
        Db.instance.INSERT(r1).EXEC();

        Rating r2 = new Rating();
        r2.displayedAs = "second rating";
        r2.artworkId   = monaLisa.id;
        r2.starsId     = starsArr[3].id; // ••••
        r2.userId      = freddy.id;
        Db.instance.INSERT(r2).EXEC();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static Provenance prov(String name) {
        Provenance p = new Provenance(); p.displayedAs = name;
        Db.instance.INSERT(p).EXEC(); return p;
    }

    private static Epoch epoch(String name) {
        Epoch e = new Epoch(); e.displayedAs = name;
        Db.instance.INSERT(e).EXEC(); return e;
    }

    private static Artist artist(String displayedAs, String fullName,
                                  String birth, String death,
                                  String bioUrl, String pictureUrl) {
        Artist a = new Artist();
        a.displayedAs = displayedAs; a.fullName   = fullName;
        a.birthDate   = birth;       a.deathDate  = death;
        a.bioUrl      = bioUrl;      a.pictureUrl = pictureUrl;
        Db.instance.INSERT(a).EXEC(); return a;
    }

    private static Artwork artwork(String title, String material,
                                    String pictureUrl, int artistId, int provenanceId) {
        Artwork a = new Artwork();
        a.displayedAs = title; a.material    = material;
        a.pictureUrl  = pictureUrl; a.artistId = artistId; a.provenanceId = provenanceId;
        Db.instance.INSERT(a).EXEC(); return a;
    }

    private static void artistEpoch(int artistId, int epochId) {
        ArtistEpoch ae = new ArtistEpoch(); ae.artistId = artistId; ae.epochId = epochId;
        Db.instance.INSERT(ae).EXEC();
    }
}

