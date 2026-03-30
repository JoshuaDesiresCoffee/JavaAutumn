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
        return "\"" + val.toString().replace("\"", "\\\"") + "\"";
    }
}