package Implementation.tests;


import Autumn.templating.Templater;

import java.io.IOException;
import java.util.Map;

/**
 * Runs lightweight checks using the language {@code assert} keyword only (no test libraries).
 * Start the JVM with assertions enabled, e.g. {@code java -ea ...}.
 */
public final class TestRunner {

    private TestRunner() {
    }

    public static void main(String[] args) throws IOException {
        testTemplaterReplacesPlaceholder();
        testTemplaterMissingKeyRendersEmpty();
        testTemplaterNullContextSafe();
        testReadTemplateRejectsPathTraversal();
        testReadTemplateLoadsExistingFile();
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
}
