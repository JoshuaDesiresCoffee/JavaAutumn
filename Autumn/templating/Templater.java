package Autumn.templating;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final Pattern EACH_BLOCK_PATTERN = Pattern.compile("\\{\\{#each\\s+([a-zA-Z0-9_.-]+)\\s*}}(.*?)\\{\\{/each}}", Pattern.DOTALL);
    private static final Path TEMPLATE_ROOT = Path.of("Implementation", "templates").toAbsolutePath().normalize();

    private Templater() {
    }

    public static String render(String templatePath, Map<String, ?> context) throws IOException {
        String template = readTemplate(templatePath);
        return renderText(template, context);
    }

    public static String renderText(String template, Map<String, ?> context) {
        // Null context treated like empty data so callers can pass null safely
        Map<String, ?> data = context == null ? Collections.emptyMap() : context;
        String withLoops = renderEachBlocks(template, data);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(withLoops);
        StringBuilder rendered = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = resolveValue(data, key);
            // Unknown keys render to empty text for now
            String replacement = value == null ? "" : String.valueOf(value);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private static String renderEachBlocks(String template, Map<String, ?> context) {
        Matcher loopMatcher = EACH_BLOCK_PATTERN.matcher(template);
        StringBuffer out = new StringBuffer();

        while (loopMatcher.find()) {
            String collectionKey = loopMatcher.group(1);
            String blockTemplate = loopMatcher.group(2);
            Object collectionValue = resolveValue(context, collectionKey);
            String replacement = renderEachBlock(collectionValue, blockTemplate, context);
            loopMatcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }

        loopMatcher.appendTail(out);
        return out.toString();
    }

    private static String renderEachBlock(Object collectionValue, String blockTemplate, Map<String, ?> parentContext) {
        List<?> items = asList(collectionValue);
        if (items == null || items.isEmpty()) {
            return "";
        }

        StringBuilder rendered = new StringBuilder();
        for (Object item : items) {
            Map<String, Object> itemContext = new LinkedHashMap<>(parentContext);
            itemContext.put("this", item);

            if (item instanceof Map<?, ?> mapItem) {
                for (Map.Entry<?, ?> entry : mapItem.entrySet()) {
                    if (entry.getKey() instanceof String key) {
                        itemContext.put(key, entry.getValue());
                    }
                }
            }

            rendered.append(renderText(blockTemplate, itemContext));
        }

        return rendered.toString();
    }

    private static List<?> asList(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof List<?> list) {
            return list;
        }

        if (value instanceof Iterable<?> iterable) {
            List<Object> list = new ArrayList<>();
            for (Object item : iterable) {
                list.add(item);
            }
            return list;
        }

        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(Array.get(value, i));
            }
            return list;
        }

        return null;
    }

    private static Object resolveValue(Map<String, ?> context, String keyPath) {
        if (context == null || keyPath == null || keyPath.isBlank()) {
            return null;
        }

        String[] parts = keyPath.split("\\.");
        Object current = context.get(parts[0]);

        if (current == null && parts.length == 1) {
            return null;
        }

        for (int i = 1; i < parts.length; i++) {
            current = resolveMember(current, parts[i]);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    private static Object resolveMember(Object value, String key) {
        if (value == null) {
            return null;
        }

        if (value instanceof Map<?, ?> map) {
            return map.get(key);
        }

        try {
            Field field = value.getClass().getDeclaredField(key);
            field.setAccessible(true);
            return field.get(value);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static String readTemplate(String templatePath) throws IOException {
        String normalizedTemplatePath = templatePath.replace('\\', '/');
        Path relativePath = Path.of(normalizedTemplatePath).normalize();

        if (relativePath.isAbsolute() || relativePath.startsWith("..")) {
            throw new IllegalArgumentException("Template path escapes template root: " + templatePath);
        }

        Path filePath = TEMPLATE_ROOT.resolve(relativePath).normalize();
        if (!filePath.startsWith(TEMPLATE_ROOT)) {
            throw new IllegalArgumentException("Template path escapes template root: " + templatePath);
        }

        if (Files.exists(filePath)) {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        }

        var stream = Templater.class.getClassLoader().getResourceAsStream(normalizedTemplatePath);
        if (stream == null) throw new IOException("Template not found: " + templatePath);
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
}