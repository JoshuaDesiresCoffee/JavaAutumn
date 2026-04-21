package Implementation.tests;


import Autumn.orm.Db;
import Autumn.orm.ForeignKey;
import Autumn.orm.Id;
import Autumn.orm.Query;
import Autumn.orm.Table;
import Autumn.templating.Json;
import Autumn.templating.Templater;
import Implementation.SampleData;
import Implementation.repository.Artist;
import Implementation.repository.ArtistEpoch;
import Implementation.repository.Artwork;
import Implementation.repository.Provenance;
import Implementation.repository.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs lightweight checks using the language {@code assert} keyword only (no test libraries).
 * Start the JVM with assertions enabled, e.g. {@code java -ea ...}.
 */
public final class TestRunner {

    private TestRunner() {
    }

    public static void main(String[] args) throws IOException {
        Path dbFile = Files.createTempFile("javaautumn-test-", ".db");
        try {
            Db.configure("jdbc:sqlite:" + dbFile);
            SampleData.syncSchema();
            Db.instance.sync(Post.class);

            testTemplaterReplacesPlaceholder();
            testTemplaterMissingKeyRendersEmpty();
            testTemplaterNullContextSafe();
            testTemplaterEachLoopRendersListOfMaps();
            testTemplaterEachLoopRendersScalarListViaThis();
            testReadTemplateRejectsPathTraversal();
            testReadTemplateLoadsExistingFile();
            testJsonEscapesSpecialChars();
            testJsonEscapesBackslash();
            testJsonEscapesControlChars();
            testExchangeCharsetUtf8();
            testOrmInsertUsesPreparedStatement();
            testOrmInsertSetsGeneratedId();
            testOrmWhereObjectRejectsEmptyFilter();
            testOrmForeignKeySchema();
            testOrmForeignKeyRejectsMissingParent();
            testOrmForeignKeyAllowsExistingParent();
            testOrmRejectsForeignKeyOnId();
            testSampleDataCreatesArtDomain();
            testSampleArtworkRequiresExistingArtist();
            testShowUserEmptyDbDoesNotCrash();
            System.out.println("All MVP.tests passed.");
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }

    private static void testTemplaterReplacesPlaceholder() {
        String out = Templater.renderText("Hello {{name}}", Map.of("name", "Alice"));
        assert "Hello Alice".equals(out) : "expected Hello Alice, got: " + out;
    }

    private static void testTemplaterMissingKeyRendersEmpty() {
        String out = Templater.renderText("Hi {{nothing}}", Map.of());
        assert "Hi ".equals(out) : "expected 'Hi ', got: " + out;
    }

    private static void testTemplaterNullContextSafe() {
        String out = Templater.renderText("x{{a}}y", null);
        assert "xy".equals(out) : "expected xy, got: " + out;
    }

    private static void testTemplaterEachLoopRendersListOfMaps() {
        String template = "<ul>{{#each users}}<li>{{id}}:{{name}}</li>{{/each}}</ul>";
        String out = Templater.renderText(template, Map.of(
                "users", List.of(
                        Map.of("id", 1, "name", "Alice"),
                        Map.of("id", 2, "name", "Bob")
                )
        ));
        assert "<ul><li>1:Alice</li><li>2:Bob</li></ul>".equals(out) : "unexpected loop output: " + out;
    }

    private static void testTemplaterEachLoopRendersScalarListViaThis() {
        String template = "{{#each nums}}[{{this}}]{{/each}}";
        String out = Templater.renderText(template, Map.of("nums", List.of(1, 2, 3)));
        assert "[1][2][3]".equals(out) : "unexpected scalar loop output: " + out;
    }

    private static void testReadTemplateRejectsPathTraversal() throws IOException {
        try {
            Templater.readTemplate("../secret.txt");
            assert false : "expected IllegalArgumentException for path traversal";
        } catch (IllegalArgumentException expected) {
            assert expected.getMessage() != null && expected.getMessage().contains("escapes");
        }
    }

    private static void testReadTemplateLoadsExistingFile() throws IOException {
        String html = Templater.readTemplate("index.html");
        assert html != null && html.contains("<html") : "index.html should load and contain <html";
    }

    private static void testJsonEscapesSpecialChars() {
        String json = Json.toJson(new TestObj("line1\nline2\ttab"));
        assert json.contains("\\n") : "newline should be escaped: " + json;
        assert json.contains("\\t") : "tab should be escaped: " + json;
        assert !json.contains("\n") : "raw newline should not appear: " + json;
    }

    private static void testJsonEscapesBackslash() {
        String json = Json.toJson(new TestObj("C:\\Users\\test"));
        assert json.contains("\\\\") : "backslash should be escaped: " + json;
    }

    private static void testJsonEscapesControlChars() {
        String json = Json.toJson(new TestObj("quote\" slash\\ \b\f\n\r\t\u0001"));
        String expected = "{\"value\":\"quote\\\" slash\\\\ \\b\\f\\n\\r\\t\\u0001\"}";
        assert expected.equals(json) : "control characters should be escaped: " + json;
    }

    private static void testExchangeCharsetUtf8() {
        String text = "Hallö Wörld";
        byte[] withCharset = text.getBytes(StandardCharsets.UTF_8);
        assert withCharset.length > text.length() : "UTF-8 bytes for umlauts should be longer than char count";
    }

    private static void testOrmInsertUsesPreparedStatement() {
        User u = new User();
        u.name = "O'Brien";
        u.email = "ob-" + System.nanoTime() + "@test.com";
        try {
            Db.instance.INSERT(u).EXEC();
            assert u.id > 0 : "generated id should be set after insert";
            var found = Db.instance.SELECT.FROM(User.class).WHERE("email = ?", u.email).EXEC();
            assert !found.isEmpty() : "inserted user should be found";
            assert "O'Brien".equals(found.getFirst().name) : "name with apostrophe should survive: " + found.getFirst().name;
        } finally {
            if (u.id > 0) {
                Db.instance.DELETE.FROM(User.class).BY_ID(u.id).EXEC();
            }
        }
    }

    private static void testOrmInsertSetsGeneratedId() {
        User u = new User();
        u.name = "Generated Id";
        u.email = "generated-id-" + System.nanoTime() + "@test.com";
        try {
            Db.instance.INSERT(u).EXEC();
            assert u.id > 0 : "insert should populate user.id";
            var found = Db.instance.SELECT.FROM(User.class).BY_ID(u.id).EXEC();
            assert found.size() == 1 : "inserted user should be found by id";
        } finally {
            if (u.id > 0) {
                Db.instance.DELETE.FROM(User.class).BY_ID(u.id).EXEC();
            }
        }
    }

    private static void testOrmWhereObjectRejectsEmptyFilter() {
        try {
            Db.instance.SELECT.FROM(User.class).WHERE(new User()).EXEC();
            assert false : "empty object filters should fail instead of selecting all rows";
        } catch (RuntimeException expected) {
            assert expected.getMessage() != null && expected.getMessage().contains("no values");
        }
    }

    private static void testOrmForeignKeySchema() {
        try (Connection conn = Db.instance.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_list(post)")) {
            assert rs.next() : "post should have a foreign key";
            assert "user".equals(rs.getString("table")) : "foreign key should reference user table";
            assert "userId".equals(rs.getString("from")) : "foreign key should use post.userId";
            assert "id".equals(rs.getString("to")) : "foreign key should reference user.id";
        } catch (Exception e) {
            throw new RuntimeException("foreign key schema check failed", e);
        }
    }

    private static void testOrmForeignKeyRejectsMissingParent() {
        Post post = new Post();
        post.userId = -12345;
        post.title = "Missing user";

        try {
            Db.instance.INSERT(post).EXEC();
            assert false : "insert should fail when userId does not exist";
        } catch (RuntimeException expected) {
            Throwable cause = expected.getCause();
            assert cause instanceof SQLException : "foreign key failure should keep SQL cause";
            String message = cause.getMessage().toLowerCase(Locale.ROOT);
            assert message.contains("foreign key") : "expected foreign key failure, got: " + message;
        }
    }

    private static void testOrmForeignKeyAllowsExistingParent() {
        User user = new User();
        user.name = "Foreign Key User";
        user.email = "fk-" + System.nanoTime() + "@test.com";
        Post post = new Post();
        try {
            Db.instance.INSERT(user).EXEC();
            post.userId = user.id;
            post.title = "Valid user";

            Db.instance.INSERT(post).EXEC();
            assert post.id > 0 : "insert should populate post.id";
        } finally {
            if (post.id > 0) {
                Db.instance.DELETE.FROM(Post.class).BY_ID(post.id).EXEC();
            }
            if (user.id > 0) {
                Db.instance.DELETE.FROM(User.class).BY_ID(user.id).EXEC();
            }
        }
    }

    private static void testOrmRejectsForeignKeyOnId() {
        try {
            Db.instance.sync(BadForeignKey.class);
            assert false : "@ForeignKey on @Id should fail loudly";
        } catch (RuntimeException expected) {
            assert expected.getMessage() != null && expected.getMessage().contains("both @Id and @ForeignKey");
        }
    }

    private static void testSampleDataCreatesArtDomain() {
        SampleData.ensure();
        assert Db.instance.SELECT.FROM(Artist.class).EXEC().size() == 2 : "sample should include two artists";
        assert Db.instance.SELECT.FROM(Artwork.class).EXEC().size() == 2 : "sample should include two artworks";
        assert Db.instance.SELECT.FROM(ArtistEpoch.class).EXEC().size() == 2 : "sample should link artists to epochs";
    }

    private static void testSampleArtworkRequiresExistingArtist() {
        Provenance provenance = Db.instance.SELECT.FROM(Provenance.class).LIMIT(1).EXEC().getFirst();
        Artwork artwork = new Artwork();
        artwork.displayedAs = "Invalid Artwork";
        artwork.material = "Oil on canvas";
        artwork.pictureUrl = "";
        artwork.artistId = -12345;
        artwork.provenanceId = provenance.id;

        try {
            Db.instance.INSERT(artwork).EXEC();
            assert false : "artwork insert should fail when artistId does not exist";
        } catch (RuntimeException expected) {
            Throwable cause = expected.getCause();
            assert cause instanceof SQLException : "foreign key failure should keep SQL cause";
            String message = cause.getMessage().toLowerCase(Locale.ROOT);
            assert message.contains("foreign key") : "expected foreign key failure, got: " + message;
        }
    }

    private static void testShowUserEmptyDbDoesNotCrash() {
        var users = Db.instance.SELECT.FROM(User.class).WHERE("id = ?", -1).EXEC();
        assert users.isEmpty() : "query for non-existent id should return empty list";
    }

    static class TestObj {
        public String value;
        TestObj(String value) { this.value = value; }
    }

    @Table(name = "post")
    public static class Post {
        @Id
        public int id;
        @ForeignKey(table = User.class)
        public int userId;
        public String title;
    }

    @Table(name = "bad_foreign_key")
    public static class BadForeignKey {
        @Id
        @ForeignKey(table = User.class)
        public int id;
    }
}
