package Autumn.templating;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal template engine:
 * - Loads template text by path
 * - Replaces {{ key }} placeholders with values from a context map
 */
public final class Templater {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private Templater() {
    }

    public static String render(String templatePath, Map<String, ?> context) throws IOException {
        String template = readTemplate(templatePath);
        return renderText(template, context);
    }

    public static String renderText(String template, Map<String, ?> context) {
        // Null context treated like empty data so callers can pass null safely
        Map<String, ?> data = context == null ? Collections.emptyMap() : context;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder rendered = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = data.get(key);
            // Unknown keys render to empty text for now
            String replacement = value == null ? "" : String.valueOf(value);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(rendered);
        return rendered.toString();
    }

    public static String readTemplate(String templatePath) throws IOException {
        var stream = Templater.class.getClassLoader().getResourceAsStream(templatePath);
        if (stream == null) throw new IOException("Template not found: " + templatePath);
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
