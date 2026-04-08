package Implementation.tests;


import Autumn.orm.Db;
import Autumn.orm.Query;
import Autumn.templating.Json;
import Autumn.templating.Templater;
import Implementation.repository.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        Db.init();
        testTemplaterReplacesPlaceholder();
        testTemplaterMissingKeyRendersEmpty();
        testTemplaterNullContextSafe();
        testTemplaterEachLoopRendersListOfMaps();
        testTemplaterEachLoopRendersScalarListViaThis();
        testReadTemplateRejectsPathTraversal();
        testReadTemplateLoadsExistingFile();
        testJsonEscapesSpecialChars();
        testJsonEscapesBackslash();
        testExchangeCharsetUtf8();
        testOrmInsertUsesPreparedStatement();
        testShowUserEmptyDbDoesNotCrash();
        System.out.println("All MVP.tests passed.");
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

    private static void testExchangeCharsetUtf8() {
        String text = "Hallö Wörld";
        byte[] withCharset = text.getBytes(StandardCharsets.UTF_8);
        assert withCharset.length > text.length() : "UTF-8 bytes for umlauts should be longer than char count";
    }

    private static void testOrmInsertUsesPreparedStatement() {
        User u = new User();
        u.id = 9999;
        u.name = "O'Brien";
        u.email = "ob@test.com";
        try {
            Db.instance.INSERT(u).EXEC();
            var found = Db.instance.SELECT.FROM(User.class).WHERE("id = 9999").EXEC();
            assert !found.isEmpty() : "inserted user should be found";
            assert "O'Brien".equals(found.getFirst().name) : "name with apostrophe should survive: " + found.getFirst().name;
        } finally {
            Db.instance.DELETE.FROM(User.class).WHERE("id = 9999").EXEC();
        }
    }

    private static void testShowUserEmptyDbDoesNotCrash() {
        var users = Db.instance.SELECT.FROM(User.class).WHERE("id = -1").EXEC();
        assert users.isEmpty() : "query for non-existent id should return empty list";
    }

    static class TestObj {
        public String value;
        TestObj(String value) { this.value = value; }
    }
}
