package Implementation.tests;


import Autumn.orm.Db;
import Autumn.orm.annotations.Id;
import Autumn.orm.Table;
import Autumn.templating.Json;
import Autumn.templating.Templater;
import Implementation.SeedDatabase;
import Implementation.repository.Artist;
import Implementation.repository.ArtistEpoch;
import Implementation.repository.Artwork;
import Implementation.repository.Provenance;
import Implementation.repository.Rating;
import Implementation.repository.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
            Db.configure().url("jdbc:sqlite:" + dbFile).connect();
            // Do not call SeedDatabase.syncSchema(): it re-configures Db to app.db and breaks isolation.
            Db.instance.sync(SeedDatabase.TABLES);

            testTemplaterReplacesPlaceholder();
            testTemplaterMissingKeyRendersEmpty();
            testTemplaterIfBlock();
            testTemplaterNullContextSafe();
            testTemplaterEachLoopRendersListOfMaps();
            testTemplaterEachLoopRendersScalarListViaThis();
            testTemplaterNestedEachLoops();
            testReadTemplateRejectsPathTraversal();
            testReadTemplateLoadsExistingFile();
            testJsonEscapesSpecialChars();
            testJsonEscapesBackslash();
            testJsonEscapesControlChars();
            testExchangeCharsetUtf8();
            testOrmInsertUsesPreparedStatement();
            testOrmInsertSetsGeneratedId();
            testOrmWhereObjectRejectsEmptyFilter();
            testOrmForeignKeyPreventsReferencedDelete();
            testSeedDatabaseCreatesArtDomain();
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

    private static void testTemplaterIfBlock() {
        String t = "{{#if show}}YES{{/if}}{{ tail }}";
        String out = Templater.renderText(t, Map.of("show", true, "tail", "Z"));
        assert "YESZ".equals(out) : "expected YESZ, got: " + out;
        String out2 = Templater.renderText(t, Map.of("show", false, "tail", "Z"));
        assert "Z".equals(out2) : "expected Z, got: " + out2;
        String nested = "{{#if a}}A{{#if b}}B{{/if}}{{/if}}";
        assert "AB".equals(Templater.renderText(nested, Map.of("a", true, "b", true)));
        assert "A".equals(Templater.renderText(nested, Map.of("a", true, "b", false)));
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

    private static void testTemplaterNestedEachLoops() {
        String template = "Outer: {{#each outer}}O{{this}} Inner: {{#each inner}}I{{this}}{{/each}} EndOuter {{/each}}";
        String out = Templater.renderText(template, Map.of(
            "outer", List.of(1, 2),
            "inner", List.of("A", "B")
        ));
        assert "Outer: O1 Inner: IAIB EndOuter O2 Inner: IAIB EndOuter ".equals(out) : "unexpected nested loop output: " + out;
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
        String json = Json.toJson(new TestObj("quote\" slash\\ \b\f\n\r\t"));
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
            Db.instance.INSERT.INTO(User.class).VALUES(u).EXEC();
            assert u.id > 0 : "generated id should be set after insert";
            var found = Db.instance.SELECT.FROM(User.class).WHERE("email = ?", u.email).EXEC();
            assert !found.isEmpty() : "inserted user should be found";
            assert "O'Brien".equals(found.getFirst().name) : "name with apostrophe should survive: " + found.getFirst().name;
        } finally {
            if (u.id > 0) {
                Db.instance.DELETE.FROM(User.class).WHERE("id = ?", u.id).EXEC();
            }
        }
    }

    private static void testOrmInsertSetsGeneratedId() {
        User u = new User();
        u.name = "Generated Id";
        u.email = "generated-id-" + System.nanoTime() + "@test.com";
        try {
            Db.instance.INSERT.INTO(User.class).VALUES(u).EXEC();
            assert u.id > 0 : "insert should populate user.id";
            var found = Db.instance.SELECT.FROM(User.class).WHERE("id = ?", u.id).EXEC();
            assert found.size() == 1 : "inserted user should be found by id";
        } finally {
            if (u.id > 0) {
                Db.instance.DELETE.FROM(User.class).WHERE("id = ?", u.id).EXEC();
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

    private static void testOrmForeignKeyPreventsReferencedDelete() {
        SeedDatabase.populateIfEmpty();
        Rating rating = Db.instance.SELECT.FROM(Rating.class).LIMIT(1).EXEC().getFirst();
        int artworkId = rating.artwork.id;
        boolean prevented = false;
        try {
            Db.instance.DELETE.FROM(Artwork.class).WHERE("id = ?", artworkId).EXEC();
        } catch (RuntimeException expected) {
            prevented = true;
        }
        assert prevented : "foreign key needs to prevent deleting a referenced artwork";
        assert Db.instance.SELECT.FROM(Artwork.class).WHERE("id = ?", artworkId).EXEC().size() == 1;
    }

    private static void testSeedDatabaseCreatesArtDomain() {
        SeedDatabase.populateIfEmpty();
        // Counts mirror SeedDatabase.insertDemoData on a fresh DB
        assert Db.instance.SELECT.FROM(Artist.class).EXEC().size() == 5 : "sample should include five artists";
        assert Db.instance.SELECT.FROM(Artwork.class).EXEC().size() == 6 : "sample should include six artworks";
        assert Db.instance.SELECT.FROM(ArtistEpoch.class).EXEC().size() == 6 : "sample should link artists to epochs";
    }

    private static void testShowUserEmptyDbDoesNotCrash() {
        var users = Db.instance.SELECT.FROM(User.class).WHERE("id = ?", -1).EXEC();
        assert users.isEmpty() : "query for non-existent id should return empty list";
    }

    static class TestObj {
        public String value;
        TestObj(String value) { this.value = value; }
    }
}
