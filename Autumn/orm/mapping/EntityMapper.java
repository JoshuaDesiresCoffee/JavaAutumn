package io.github.finch.core.mapping;

import io.github.finch.core.Table;
import io.github.finch.core.annotations.*;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EntityMapper {

    public static final class FieldInfo {
        public final Field   field;
        public final String  columnName;   // null for OneToMany / ManyToMany (no column in this table)
        public final boolean isId;
        public final boolean isForeignKey; // field type is a @Table class → stores _id column
        public final boolean isOneToMany;
        public final boolean isManyToMany;
        public final Class<?> relatedType; // the @Table class on the other side

        FieldInfo(Field field, String columnName, boolean isId,
                  boolean isForeignKey, boolean isOneToMany, boolean isManyToMany,
                  Class<?> relatedType) {
            this.field        = field;
            this.columnName   = columnName;
            this.isId         = isId;
            this.isForeignKey = isForeignKey;
            this.isOneToMany  = isOneToMany;
            this.isManyToMany = isManyToMany;
            this.relatedType  = relatedType;
        }
    }

    private static final Map<Class<?>, List<FieldInfo>> CACHE = new ConcurrentHashMap<>();

    public static List<FieldInfo> getFields(Class<?> cls) {
        return CACHE.computeIfAbsent(cls, EntityMapper::analyze);
    }

    private static List<FieldInfo> analyze(Class<?> cls) {
        List<FieldInfo> result = new ArrayList<>();

        for (Field f : cls.getDeclaredFields()) {
            f.setAccessible(true);

            boolean isId         = f.isAnnotationPresent(Id.class) || f.getName().equals("id");
            boolean isOneToMany  = f.isAnnotationPresent(OneToMany.class);
            boolean isManyToMany = f.isAnnotationPresent(ManyToMany.class);

            Class<?> fieldType = f.getType();
            boolean isForeignKey = !isOneToMany && !isManyToMany
                    && fieldType.isAnnotationPresent(Table.class);

            // For List<T> relations extract T; for FK relations relatedType = fieldType
            Class<?> relatedType = null;
            if (isOneToMany || isManyToMany) {
                if (f.getGenericType() instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) f.getGenericType();
                    Type[] args = pt.getActualTypeArguments();
                    if (args.length == 1 && args[0] instanceof Class) relatedType = (Class<?>) args[0];
                }
            } else if (isForeignKey) {
                relatedType = fieldType;
            }

            String colName;
            if (isOneToMany || isManyToMany) {
                colName = null; // no physical column in this table
            } else if (f.isAnnotationPresent(Column.class)) {
                String custom = f.getAnnotation(Column.class).name();
                colName = custom.isEmpty() ? f.getName() : custom;
            } else if (isForeignKey) {
                colName = f.getName() + "_id";
            } else {
                colName = f.getName();
            }

            result.add(new FieldInfo(f, colName, isId, isForeignKey, isOneToMany, isManyToMany, relatedType));
        }
        return result;
    }

    public static String tableName(Class<?> cls) {
        Table ann = cls.getAnnotation(Table.class);
        return (ann == null || ann.name().isEmpty()) ? cls.getSimpleName().toLowerCase() : ann.name();
    }

    public static FieldInfo getIdField(Class<?> cls) {
        return getFields(cls).stream()
                .filter(fi -> fi.isId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No @Id field (or field named 'id') on " + cls.getSimpleName()));
    }

    public static String sqlType(Class<?> type) {
        if (type == int.class       || type == Integer.class)    return "INTEGER";
        if (type == long.class      || type == Long.class)       return "BIGINT";
        if (type == short.class     || type == Short.class)      return "SMALLINT";
        if (type == byte.class      || type == Byte.class)       return "TINYINT";
        if (type == float.class     || type == Float.class)      return "REAL";
        if (type == double.class    || type == Double.class)     return "DOUBLE";
        if (type == boolean.class   || type == Boolean.class)    return "BOOLEAN";
        if (type == BigDecimal.class)                            return "DECIMAL";
        if (type == LocalDate.class)                             return "DATE";
        if (type == LocalDateTime.class || type == Instant.class) return "TIMESTAMP";
        if (type == LocalTime.class)                             return "TIME";
        if (type == UUID.class)                                  return "TEXT";
        if (type == byte[].class)                                return "BLOB";
        if (type.isEnum())                                       return "TEXT";
        return "TEXT";
    }
}
