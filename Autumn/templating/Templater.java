package Autumn.templating;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Minimal template engine:
 * - Loads template text by path
 * - {@code {{#if key}} ... {{/if}}} when {@code key} is truthy in context
 * - {@code {{#each key}} ... {{/each}}} lists
 * - {@code {{ key }}} placeholders
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
        String afterIf = renderIfBlocks(template, data);
        String withLoops = renderEachBlocks(afterIf, data);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(withLoops);
        StringBuilder rendered = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = resolveValue(data, key);
            String replacement = formatValue(value);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(rendered);
        return rendered.toString();
    }

    // String for {{ x }}: primitives, lists comma-joined; objects use displayedAs/name if exist.
    public static String formatValue(Object value) {
        if (value == null) return "";
        if (isSimple(value)) return value.toString();
        if (value instanceof Collection<?> c) {
            if (c.isEmpty()) return "";
            return c.stream().map(Templater::formatValue)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(", "));
        }
        String main = entityLabel(value);
        List<String> linked = entityLinkedLabels(value);
        if (main != null) {
            return linked.isEmpty() ? main : main + " (" + String.join(", ", linked) + ")";
        }
        if (!linked.isEmpty()) return String.join(", ", linked);
        return value.toString();
    }

    private static boolean isSimple(Object o) {
        return o instanceof CharSequence || o instanceof Number || o instanceof Boolean
                || o instanceof Character || o instanceof Enum<?>;
    }

    // Prefer displayedAs, else name appear in keys or reflective fields.
    private static String entityLabel(Object value) {
        for (String candidate : new String[]{"displayedAs", "name"}) {
            Object v = readMember(value, candidate);
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return null;
    }

    // Other fields that look like linked rows (nested objects with a displayedAs/name).
    private static List<String> entityLinkedLabels(Object value) {
        List<String> labels = new ArrayList<>();
        for (Map.Entry<String, Object> e : membersOf(value)) {
            Object v = e.getValue();
            if (v == null || isSimple(v) || v instanceof Collection<?>) continue;
            String lbl = entityLabel(v);
            if (lbl != null) labels.add(lbl);
        }
        return labels;
    }

    private static Object readMember(Object value, String key) {
        if (value instanceof Map<?, ?> m) return m.get(key);
        try {
            Field f = value.getClass().getDeclaredField(key);
            f.setAccessible(true);
            return f.get(value);
        } catch (NoSuchFieldException ignored) {
            return null;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Map.Entry<String, Object>> membersOf(Object value) {
        List<Map.Entry<String, Object>> out = new ArrayList<>();
        if (value instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() instanceof String k) {
                    out.add(new AbstractMap.SimpleEntry<>(k, e.getValue()));
                }
            }
            return out;
        }
        for (Field f : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
            f.setAccessible(true);
            try {
                out.add(new AbstractMap.SimpleEntry<>(f.getName(), f.get(value)));
            } catch (IllegalAccessException ignored) {}
        }
        return out;
    }

    private static String renderIfBlocks(String template, Map<String, ?> context) {
        StringBuilder out = new StringBuilder();
        int currentIndex = 0;

        while (true) {
            int startIndex = template.indexOf("{{#if", currentIndex);
            if (startIndex == -1) {
                out.append(template.substring(currentIndex));
                break;
            }

            out.append(template, currentIndex, startIndex);

            int keyEndIndex = template.indexOf("}}", startIndex);
            if (keyEndIndex == -1) {
                out.append(template.substring(startIndex));
                break;
            }

            int condStart = startIndex + "{{#if".length();
            while (condStart < template.length() && Character.isWhitespace(template.charAt(condStart))) {
                condStart++;
            }
            String condKey = template.substring(condStart, keyEndIndex).trim();

            int blockStartIndex = keyEndIndex + 2;
            int depth = 1;
            int searchIndex = blockStartIndex;
            int blockEndIndex = -1;
            int nextEndIndex = -1;

            while (depth > 0) {
                int nextOpen = template.indexOf("{{#if", searchIndex);
                int nextClose = template.indexOf("{{/if}}", searchIndex);

                if (nextClose == -1) {
                    break;
                }

                if (nextOpen != -1 && nextOpen < nextClose) {
                    depth++;
                    searchIndex = nextOpen + "{{#if".length();
                } else {
                    depth--;
                    searchIndex = nextClose + "{{/if}}".length();
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
            Object condVal = condKey.isEmpty() ? null : resolveValue(context, condKey);
            if (isTruthy(condVal)) {
                out.append(renderText(blockTemplate, context));
            }
            currentIndex = nextEndIndex;
        }

        return out.toString();
    }

    private static boolean isTruthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return !s.isBlank();
        }
        if (v instanceof Number n) {
            return n.doubleValue() != 0;
        }
        if (v instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (v instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        return true;
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