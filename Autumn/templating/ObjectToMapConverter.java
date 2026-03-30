package Autumn.templating;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class ObjectToMapConverter {

    public static Map<String, Object> convert(Object obj) {
        if (obj == null) return null;
        return convertObject(obj, new IdentityHashMap<>());
    }

    private static Map<String, Object> convertObject(Object obj, Map<Object, Map<String, Object>> visited) {
        if (obj == null) return null;

        if (visited.containsKey(obj)) return visited.get(obj);

        Map<String, Object> map = new LinkedHashMap<>();
        visited.put(obj, map);

        for (Field field : getAllFields(obj.getClass())) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;

            field.setAccessible(true);

            try {
                String key   = field.getName();
                Object value = field.get(obj);
                map.put(key, convertValue(value, visited));
            } catch (IllegalAccessException e) {
                map.put(field.getName(), "<inaccessible>");
            }
        }

        return map;
    }

    @SuppressWarnings("unchecked")
    private static Object convertValue(Object value, Map<Object, Map<String, Object>> visited) {
        if (value == null)                  return null;
        if (isSimpleType(value))            return value;
        if (value instanceof Map<?, ?> m)   return convertMap(m, visited);
        if (value instanceof Collection<?>) return convertCollection((Collection<?>) value, visited);
        if (value.getClass().isArray())     return convertArray(value, visited);

        return convertObject(value, visited);
    }

    private static List<Object> convertCollection(Collection<?> collection, Map<Object, Map<String, Object>> visited) {
        List<Object> list = new ArrayList<>(collection.size());
        for (Object element : collection) {
            list.add(convertValue(element, visited));
        }
        return list;
    }

    private static List<Object> convertArray(Object array, Map<Object, Map<String, Object>> visited) {
        int length = java.lang.reflect.Array.getLength(array);
        List<Object> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            list.add(convertValue(java.lang.reflect.Array.get(array, i), visited));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> convertMap(Map<?, ?> map, Map<Object, Map<String, Object>> visited) {
        Map<Object, Object> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(
                convertValue(k, visited),
                convertValue(v, visited)
        ));
        return result;
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    private static boolean isSimpleType(Object value) {
        return value instanceof Number
                || value instanceof Boolean
                || value instanceof String
                || value instanceof Character
                || value instanceof Enum<?>
                || value.getClass().isPrimitive();
    }
}