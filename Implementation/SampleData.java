package Implementation;

import Autumn.orm.Db;
import Implementation.repository.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class SampleData {
    private static final String DATABASE_PATH = "Implementation/data/app.db";
    private static final String DATABASE_URL = "jdbc:sqlite:" + DATABASE_PATH;

    public static final Class<?>[] TABLES = {
            User.class,
            Artist.class,
            Provenance.class,
            Epoch.class,
            Artwork.class,
            ArtistEpoch.class
    };

    private SampleData() {
    }

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
        Db.instance.sync(TABLES);
    }

    public static void ensure() {
        if (!Db.instance.SELECT.FROM(Artist.class).LIMIT(1).EXEC().isEmpty()) {
            return;
        }

        User mia = new User();
        mia.name = "Mia";
        mia.email = "mia@example.test";
        Db.instance.INSERT(mia).EXEC();

        User jon = new User();
        jon.name = "Jon";
        jon.email = "jon@example.test";
        Db.instance.INSERT(jon).EXEC();

        Provenance studio = new Provenance();
        studio.displayedAs = "Studio archive";
        Db.instance.INSERT(studio).EXEC();

        Epoch contemporary = new Epoch();
        contemporary.displayedAs = "Contemporary";
        Db.instance.INSERT(contemporary).EXEC();

        Artist lina = new Artist();
        lina.displayedAs = "Lina Meier";
        lina.fullName = "Lina Meier";
        lina.birthDate = "1988-04-12";
        lina.deathDate = "";
        lina.bioUrl = "";
        lina.pictureUrl = "";
        Db.instance.INSERT(lina).EXEC();

        Artist noah = new Artist();
        noah.displayedAs = "Noah Keller";
        noah.fullName = "Noah Keller";
        noah.birthDate = "1991-09-03";
        noah.deathDate = "";
        noah.bioUrl = "";
        noah.pictureUrl = "";
        Db.instance.INSERT(noah).EXEC();

        Artwork blueVase = new Artwork();
        blueVase.displayedAs = "Blue Vase";
        blueVase.material = "Clay";
        blueVase.pictureUrl = "";
        blueVase.artistId = lina.id;
        blueVase.provenanceId = studio.id;
        Db.instance.INSERT(blueVase).EXEC();

        Artwork smallBridge = new Artwork();
        smallBridge.displayedAs = "Small Bridge";
        smallBridge.material = "Ink on paper";
        smallBridge.pictureUrl = "";
        smallBridge.artistId = noah.id;
        smallBridge.provenanceId = studio.id;
        Db.instance.INSERT(smallBridge).EXEC();

        ArtistEpoch linaEpoch = new ArtistEpoch();
        linaEpoch.artistId = lina.id;
        linaEpoch.epochId = contemporary.id;
        Db.instance.INSERT(linaEpoch).EXEC();

        ArtistEpoch noahEpoch = new ArtistEpoch();
        noahEpoch.artistId = noah.id;
        noahEpoch.epochId = contemporary.id;
        Db.instance.INSERT(noahEpoch).EXEC();
    }
}
