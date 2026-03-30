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
        return this;
    }

    public Query<T> WHERE(Object o) {
        StringBuilder sb = new StringBuilder();
        for (Field f : o.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object val = f.get(o);
                if (val != null) {
                    if (sb.length() > 0) sb.append(" AND ");
                    sb.append(f.getName()).append(" = '").append(val).append("'");
                }
            } catch (IllegalAccessException ignored) {}
        }
        this.where = sb.toString();
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
        Table ann = table.getAnnotation(Table.class);
        return ann.name().isEmpty() ? table.getSimpleName().toLowerCase() : ann.name();
    }

    private List<T> execSelect() {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName());
        if (where != null) sql.append(" WHERE ").append(where);
        if (limit > 0)     sql.append(" LIMIT ").append(limit);

        try (Connection conn = db.getConnection();
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql.toString())) {

            List<T> results = new ArrayList<>();
            while (rs.next()) {
                T obj = table.getDeclaredConstructor().newInstance();
                for (Field f : table.getDeclaredFields()) {
                    f.setAccessible(true);
                    f.set(obj, rs.getObject(f.getName()));
                }
                results.add(obj);
            }
            return results;

        } catch (Exception e) { throw new RuntimeException("SELECT failed", e); }
    }

    private void execInsert() {
        Field[] fields = object.getClass().getDeclaredFields();
        StringJoiner cols = new StringJoiner(", ");
        StringJoiner vals = new StringJoiner(", ");
        for (Field f : fields) {
            f.setAccessible(true);
            try {
                cols.add(f.getName());
                Object v = f.get(object);
                vals.add(v == null ? "NULL" : "'" + v + "'");
            } catch (IllegalAccessException ignored) {}
        }
        String sql = "INSERT INTO " + tableName(object.getClass()) + " (" + cols + ") VALUES (" + vals + ")";
        exec(sql);
    }

    private void execUpdate() {
        Field[] fields = object.getClass().getDeclaredFields();
        StringJoiner sets = new StringJoiner(", ");
        for (Field f : fields) {
            f.setAccessible(true);
            try {
                Object v = f.get(object);
                sets.add(f.getName() + " = " + (v == null ? "NULL" : "'" + v + "'"));
            } catch (IllegalAccessException ignored) {}
        }
        String sql = "UPDATE " + tableName(object.getClass()) + " SET " + sets
                + (where != null ? " WHERE " + where : "");
        exec(sql);
    }

    private void execDelete() {
        String sql = "DELETE FROM " + tableName()
                + (where != null ? " WHERE " + where : "");
        exec(sql);
    }

    private void exec(String sql) {
        try (Connection conn = db.getConnection();
             Statement stmt  = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) { throw new RuntimeException("Query failed: " + sql, e); }
    }

    private static String tableName(Class<?> t) {
        Table ann = t.getAnnotation(Table.class);
        return ann.name().isEmpty() ? t.getSimpleName().toLowerCase() : ann.name();
    }

    Query<T> withObject(T obj) {
        this.object = obj;
        @SuppressWarnings("unchecked")
        Class<T> cls = (Class<T>) obj.getClass();
        this.table = cls;
        return this;
    }
}