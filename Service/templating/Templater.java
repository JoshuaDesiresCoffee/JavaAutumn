package Service.templating;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Templater implements TemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
    private static final Templater DEFAULT = new Templater();
    private final TemplateReader templateReader;

    public Templater() {
        this(new FileTemplateReader());
    }

    public Templater(TemplateReader templateReader) {
        this.templateReader = templateReader;
    }

    @Override
    public String renderTemplate(String templatePath, Map<String, ?> context) throws IOException {
        String template = templateReader.readTemplate(templatePath);
        return renderText(template, context);
    }

    public String renderText(String template, Map<String, ?> context) {
        Map<String, ?> data = context == null ? Collections.emptyMap() : context;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder rendered = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = data.get(key);
            String replacement = value == null ? "" : String.valueOf(value);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(rendered);
        return rendered.toString();
    }

    public static String render(String templatePath, Map<String, ?> context) throws IOException {
        return DEFAULT.renderTemplate(templatePath, context);
    }

    public static String readTemplate(String templatePath) throws IOException {
        return DEFAULT.templateReader.readTemplate(templatePath);
    }
}
