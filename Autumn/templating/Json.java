package Autumn.templating;

import java.lang.reflect.Field;
import java.util.List;

public final class Json {

    private Json() {}

    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                sb.append(toJson(list.get(i)));
                if (i < list.size() - 1) sb.append(",");
            }
            return sb.append("]").toString();
        }
        StringBuilder sb = new StringBuilder("{");
        Field[] fields = obj.getClass().getDeclaredFields();
        int written = 0;
        for (Field f : fields) {
            f.setAccessible(true);
            try {
                if (written++ > 0) sb.append(",");
                sb.append("\"").append(f.getName()).append("\":");
                sb.append(valueToJson(f.get(obj)));
            } catch (IllegalAccessException ignored) {}
        }
        return sb.append("}").toString();
    }

    private static String valueToJson(Object val) {
        if (val == null)                return "null";
        if (val instanceof Number)      return val.toString();
        if (val instanceof Boolean)     return val.toString();
        if (val instanceof List<?>)     return toJson(val);
        return "\"" + escapeString(val.toString()) + "\"";
    }

    private static String escapeString(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
