package io.github.finch.core.mapping;

import io.github.finch.core.Table;
import io.github.finch.core.annotations.Column;
import io.github.finch.core.pool.ConnectionPool;

import java.sql.*;
import java.util.*;

public class SchemaSync {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates missing tables and migrates existing ones by adding any new columns.
     * Columns that are removed from the entity are left untouched in the database —
     * automatic drops are intentionally unsupported to prevent data loss.
     */
    public static void sync(ConnectionPool pool, Class<?>... tables) {
        Connection conn = null;
        try {
            conn = pool.borrow();
            Dialect dialect = Dialect.detect(conn);
            validateAll(tables);
            for (Class<?> t : tables) {
                syncTable(conn, t, dialect);
                migrateTable(conn, t, dialect);
            }
            Set<String> seen = new HashSet<>();
            for (Class<?> t : tables) {
                for (EntityMapper.FieldInfo fi : EntityMapper.getFields(t)) {
                    if (fi.isManyToMany && fi.relatedType != null) {
                        String key = junctionName(EntityMapper.tableName(t), EntityMapper.tableName(fi.relatedType));
                        if (seen.add(key)) syncJunctionTable(conn, t, fi.relatedType);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Schema sync failed", e);
        } finally {
            if (conn != null) pool.release(conn);
        }
    }

    /**
     * Returns the full CREATE TABLE DDL for the given entity classes as a SQL string.
     * Types are chosen for the active database dialect. Junction tables for
     * {@code @ManyToMany} relationships are included and deduplicated.
     */
    public static String exportSchema(ConnectionPool pool, Class<?>... tables) {
        Connection conn = null;
        try {
            conn = pool.borrow();
            Dialect dialect = Dialect.detect(conn);
            validateAll(tables);
            StringBuilder sb = new StringBuilder();
            // LinkedHashMap preserves insertion order and deduplicates junction tables.
            Map<String, String> junctions = new LinkedHashMap<>();
            for (Class<?> t : tables) {
                sb.append(buildCreateSql(t, dialect, false)).append(";\n\n");
                for (EntityMapper.FieldInfo fi : EntityMapper.getFields(t)) {
                    if (fi.isManyToMany && fi.relatedType != null) {
                        String name = junctionName(EntityMapper.tableName(t), EntityMapper.tableName(fi.relatedType));
                        junctions.computeIfAbsent(name, k -> buildJunctionSql(t, fi.relatedType));
                    }
                }
            }
            for (String sql : junctions.values()) sb.append(sql).append(";\n\n");
            return sb.toString().trim();
        } catch (SQLException e) {
            throw new RuntimeException("Schema export failed", e);
        } finally {
            if (conn != null) pool.release(conn);
        }
    }

    // ── Table Sync ────────────────────────────────────────────────────────────

    private static void syncTable(Connection conn, Class<?> t, Dialect dialect) {
        exec(conn, buildCreateSql(t, dialect, true), EntityMapper.tableName(t));
    }

    /**
     * Adds columns that are present in the entity but missing from the database table.
     * NOT NULL is intentionally omitted from ALTER TABLE statements — existing rows
     * cannot satisfy a NOT NULL constraint without a DEFAULT value.
     * UNIQUE is skipped for SQLite, which does not support it in ADD COLUMN.
     */
    private static void migrateTable(Connection conn, Class<?> t, Dialect dialect) {
        String tableName = EntityMapper.tableName(t);
        Set<String> existing = getExistingColumns(conn, tableName);
        if (existing.isEmpty()) return; // table wasn't created (shouldn't happen after syncTable)

        for (EntityMapper.FieldInfo fi : EntityMapper.getFields(t)) {
            if (fi.columnName == null) continue;                               // OneToMany/ManyToMany — no column
            if (existing.contains(fi.columnName.toLowerCase())) continue;      // column already present
            exec(conn, "ALTER TABLE " + tableName + " ADD COLUMN " + buildAlterColumnDef(fi, dialect), tableName);
        }
    }

    private static void syncJunctionTable(Connection conn, Class<?> a, Class<?> b) {
        exec(conn, buildJunctionSql(a, b), junctionName(EntityMapper.tableName(a), EntityMapper.tableName(b)));
    }

    // ── SQL Builders ──────────────────────────────────────────────────────────

    private static String buildCreateSql(Class<?> t, Dialect dialect, boolean ifNotExists) {
        List<String> cols = new ArrayList<>();
        for (EntityMapper.FieldInfo fi : EntityMapper.getFields(t)) {
            if (fi.columnName == null) continue;
            cols.add(buildColumnDef(fi, dialect));
        }
        String prefix = ifNotExists ? "CREATE TABLE IF NOT EXISTS " : "CREATE TABLE ";
        return prefix + EntityMapper.tableName(t) + " (" + String.join(", ", cols) + ")";
    }

    /** Full column definition used in CREATE TABLE (includes NOT NULL and UNIQUE). */
    private static String buildColumnDef(EntityMapper.FieldInfo fi, Dialect dialect) {
        if (fi.isId) return dialect.primaryKeyDef(fi.columnName);
        String type = fi.isForeignKey ? "INTEGER" : dialect.sqlType(fi.field.getType());
        StringBuilder col = new StringBuilder(fi.columnName).append(" ").append(type);
        if (fi.field.isAnnotationPresent(Column.class)) {
            Column ann = fi.field.getAnnotation(Column.class);
            if (!ann.nullable()) col.append(" NOT NULL");
            if (ann.unique())    col.append(" UNIQUE");
        }
        return col.toString();
    }

    /** Reduced column definition safe for ALTER TABLE ADD COLUMN across all supported dialects. */
    private static String buildAlterColumnDef(EntityMapper.FieldInfo fi, Dialect dialect) {
        if (fi.isId) return dialect.primaryKeyDef(fi.columnName);
        String type = fi.isForeignKey ? "INTEGER" : dialect.sqlType(fi.field.getType());
        StringBuilder col = new StringBuilder(fi.columnName).append(" ").append(type);
        if (fi.field.isAnnotationPresent(Column.class)) {
            Column ann = fi.field.getAnnotation(Column.class);
            // NOT NULL omitted — existing rows have no value for the new column.
            // UNIQUE omitted for SQLite — not supported in ADD COLUMN.
            if (ann.unique() && dialect != Dialect.SQLITE) col.append(" UNIQUE");
        }
        return col.toString();
    }

    private static String buildJunctionSql(Class<?> a, Class<?> b) {
        String nameA = EntityMapper.tableName(a);
        String nameB = EntityMapper.tableName(b);
        String junction = junctionName(nameA, nameB);
        String colA = nameA + "_id";
        String colB = nameB + "_id";
        return "CREATE TABLE IF NOT EXISTS " + junction + " ("
                + colA + " INTEGER, " + colB + " INTEGER, "
                + "PRIMARY KEY (" + colA + ", " + colB + "))";
    }

    private static String junctionName(String nameA, String nameB) {
        String[] sorted = {nameA, nameB};
        Arrays.sort(sorted);
        return sorted[0] + "_" + sorted[1];
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /**
     * Reads existing column names from the live database using portable JDBC metadata.
     * Tries exact case, then UPPER (H2 default), then lower to handle engine quirks.
     * Returns lowercase names for case-insensitive comparison.
     */
    private static Set<String> getExistingColumns(Connection conn, String tableName) {
        try {
            Set<String> cols = new LinkedHashSet<>();
            DatabaseMetaData meta = conn.getMetaData();
            for (String name : new String[]{tableName, tableName.toUpperCase(), tableName.toLowerCase()}) {
                try (ResultSet rs = meta.getColumns(null, null, name, null)) {
                    while (rs.next()) cols.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
                if (!cols.isEmpty()) break;
            }
            return cols;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read existing columns for " + tableName, e);
        }
    }

    private static void validateAll(Class<?>... tables) {
        for (Class<?> t : tables) {
            if (!t.isAnnotationPresent(Table.class))
                throw new RuntimeException(t.getName() + " must be annotated with @Table");
        }
    }

    private static void exec(Connection conn, String sql, String context) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Schema operation failed for [" + context + "]: " + sql, e);
        }
    }
}
