package Autumn.orm;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;

public class Query<T> {

    enum Mode { SELECT, INSERT, UPDATE, DELETE }

    private final Db db;
    private Class<T> table;
    private T object;
    private String where;
    private List<Object> whereParams = Collections.emptyList();
    private int limit = -1;
    private Mode mode;

    Query(Db db, Mode mode) {
        this.db   = db;
        this.mode = mode;
    }

    public Query<T> FROM(Class<T> tableClass) {
        if (!tableClass.isAnnotationPresent(Table.class))
            throw new RuntimeException(tableClass.getName() + " must be annotated with @Table");
        this.table = tableClass;
        return this;
    }

    public Query<T> WHERE(String condition) {
        this.where = condition;
        this.whereParams = Collections.emptyList();
        return this;
    }

    public Query<T> WHERE(String condition, Object... params) {
        this.where = condition;
        this.whereParams = List.of(params);
        return this;
    }

    public Query<T> WHERE(Object o) {
        StringBuilder sb = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (Field f : Db.persistentFields(o.getClass())) {
            f.setAccessible(true);
            try {
                Object val = f.get(o);
                if (Db.isGeneratedId(f) && isDefaultId(val)) {
                    continue;
                }

                if (val != null) {
                    if (!sb.isEmpty()) sb.append(" AND ");
                    sb.append(f.getName()).append(" = ?");
                    params.add(val);
                }
            } catch (IllegalAccessException ignored) {}
        }
        if (params.isEmpty()) {
            throw new RuntimeException("WHERE object has no values to match");
        }
        this.where = sb.toString();
        this.whereParams = params;
        return this;
    }

    public Query<T> BY_ID(Object id) {
        Field idField = Db.idField(table).orElseThrow(() ->
                new RuntimeException(table.getName() + " has no @Id field"));
        this.where = idField.getName() + " = ?";
        this.whereParams = List.of(id);
        return this;
    }

    public Query<T> LIMIT(int n) {
        this.limit = n;
        return this;
    }

    public List<T> EXEC() {
        return switch (mode) {
            case SELECT -> execSelect();
            case INSERT -> { execInsert(); yield Collections.emptyList(); }
            case UPDATE -> { execUpdate(); yield Collections.emptyList(); }
            case DELETE -> { execDelete(); yield Collections.emptyList(); }
        };
    }

    private String tableName() {
        return Db.tableName(table);
    }

    private List<T> execSelect() {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName());
        if (where != null) sql.append(" WHERE ").append(where);
        if (limit > 0)     sql.append(" LIMIT ").append(limit);

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            bind(stmt, whereParams);

            try (ResultSet rs = stmt.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    T obj = table.getDeclaredConstructor().newInstance();
                    for (Field f : Db.persistentFields(table)) {
                        f.setAccessible(true);
                        Object value = rs.getObject(f.getName());
                        if (value == null && f.getType().isPrimitive()) {
                            continue;
                        }
                        f.set(obj, value);
                    }
                    results.add(obj);
                }
                return results;
            }

        } catch (Exception e) { throw new RuntimeException("SELECT failed", e); }
    }

    private void execInsert() {
        Field[] fields = Db.persistentFields(object.getClass());
        StringJoiner cols = new StringJoiner(", ");
        StringJoiner placeholders = new StringJoiner(", ");
        List<Object> params = new ArrayList<>();
        Field generatedIdField = null;
        for (Field f : fields) {
            f.setAccessible(true);
            try {
                if (Db.isGeneratedId(f) && db.isPrimaryKeyColumn(object.getClass(), f.getName())) {
                    generatedIdField = f;
                    continue;
                }

                Object value = f.get(object);
                if (Db.isGeneratedId(f) && isDefaultId(value)) {
                    value = db.nextId(object.getClass(), f.getName());
                    setFieldValue(f, object, value);
                }

                cols.add(f.getName());
                placeholders.add("?");
                params.add(value);
            } catch (IllegalAccessException ignored) {}
        }
        String sql = "INSERT INTO " + tableName(object.getClass()) + " (" + cols + ") VALUES (" + placeholders + ")";
        execInsertPrepared(sql, params, generatedIdField);
    }

    private void execUpdate() {
        Field[] fields = Db.persistentFields(object.getClass());
        StringJoiner sets = new StringJoiner(", ");
        List<Object> params = new ArrayList<>();
        for (Field f : fields) {
            if (f.isAnnotationPresent(Id.class)) continue;
            f.setAccessible(true);
            try {
                sets.add(f.getName() + " = ?");
                params.add(f.get(object));
            } catch (IllegalAccessException ignored) {}
        }
        if (sets.length() == 0) {
            throw new RuntimeException("No fields to update for " + object.getClass().getName());
        }
        params.addAll(whereParams);
        String sql = "UPDATE " + tableName(object.getClass()) + " SET " + sets
                + (where != null ? " WHERE " + where : "");
        execPrepared(sql, params);
    }

    private void execDelete() {
        String sql = "DELETE FROM " + tableName()
                + (where != null ? " WHERE " + where : "");
        execPrepared(sql, whereParams);
    }

    private void execPrepared(String sql, List<Object> params) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bind(stmt, params);
            stmt.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("Query failed: " + sql, e); }
    }

    private void execInsertPrepared(String sql, List<Object> params, Field generatedIdField) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(stmt, params);
            stmt.executeUpdate();
            if (generatedIdField != null) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        setFieldValue(generatedIdField, object, keys.getLong(1));
                    }
                }
            }
        } catch (SQLException | IllegalAccessException e) {
            throw new RuntimeException("Query failed: " + sql, e);
        }
    }

    private static void bind(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            stmt.setObject(i + 1, params.get(i));
        }
    }

    private static boolean isDefaultId(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Number number) {
            return number.longValue() == 0;
        }
        return false;
    }

    private static void setFieldValue(Field field, Object target, Object value) throws IllegalAccessException {
        if (field.getType() == int.class || field.getType() == Integer.class) {
            field.set(target, Math.toIntExact(((Number) value).longValue()));
            return;
        }

        if (field.getType() == long.class || field.getType() == Long.class) {
            field.set(target, ((Number) value).longValue());
            return;
        }

        field.set(target, value);
    }

    private static String tableName(Class<?> t) {
        return Db.tableName(t);
    }

    Query<T> withObject(T obj) {
        this.object = obj;
        @SuppressWarnings("unchecked")
        Class<T> cls = (Class<T>) obj.getClass();
        this.table = cls;
        return this;
    }
}
