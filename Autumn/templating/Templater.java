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
        StringBuilder out = new StringBuilder();
        int currentIndex = 0;

        while (true) {
            int startIndex = findEachOpen(template, currentIndex);
            if (startIndex == -1) {
                out.append(template.substring(currentIndex));
                break;
            }

            out.append(template, currentIndex, startIndex);

            int keyEndIndex = template.indexOf("}}", startIndex);
            if (keyEndIndex == -1) {
                out.append(template, startIndex, startIndex + 7);
                currentIndex = startIndex + 7;
                continue;
            }

            String tagContent = template.substring(startIndex + 7, keyEndIndex).trim();
            int blockStartIndex = keyEndIndex + 2;

            int depth = 1;
            int searchIndex = blockStartIndex;
            int blockEndIndex = -1;
            int nextEndIndex = -1;

            while (depth > 0) {
                int nextOpen = findEachOpen(template, searchIndex);
                int nextClose = template.indexOf("{{/each}}", searchIndex);

                if (nextClose == -1) {
                    break;
                }

                if (nextOpen != -1 && nextOpen < nextClose) {
                    depth++;
                    searchIndex = nextOpen + 7;
                } else {
                    depth--;
                    searchIndex = nextClose + 9;
                    if (depth == 0) {
                        blockEndIndex = nextClose;
                        nextEndIndex = searchIndex;
                    }
                }
            }

            if (blockEndIndex == -1) {
                out.append(template, startIndex, keyEndIndex + 2);
                currentIndex = keyEndIndex + 2;
                continue;
            }

            String blockTemplate = template.substring(blockStartIndex, blockEndIndex);
            Object collectionValue = resolveValue(context, tagContent);
            String replacement = renderEachBlock(collectionValue, blockTemplate, context);
            out.append(replacement);
            currentIndex = nextEndIndex;
        }

        return out.toString();
    }

    private static int findEachOpen(String text, int fromIndex) {
        int idx = text.indexOf("{{#each", fromIndex);
        while (idx != -1) {
            if (idx + 7 < text.length() && Character.isWhitespace(text.charAt(idx + 7))) {
                return idx;
            }
            idx = text.indexOf("{{#each", idx + 7);
        }
        return -1;
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