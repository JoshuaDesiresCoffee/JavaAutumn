package io.github.finch.core.mapping;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.*;
import java.util.UUID;

/**
 * Database dialect — drives DDL differences between engines.
 * Detected at runtime from {@link java.sql.DatabaseMetaData#getDatabaseProductName()}.
 */
public enum Dialect {
    SQLITE, POSTGRESQL, MYSQL, H2, GENERIC;

    public static Dialect detect(Connection conn) {
        try {
            String p = conn.getMetaData().getDatabaseProductName().toLowerCase();
            if (p.contains("sqlite"))                              return SQLITE;
            if (p.contains("postgres"))                           return POSTGRESQL;
            if (p.contains("mysql") || p.contains("mariadb"))    return MYSQL;
            if (p.contains("h2"))                                 return H2;
        } catch (SQLException ignored) {}
        return GENERIC;
    }

    /**
     * Returns a complete PRIMARY KEY column definition for an auto-incrementing id.
     * SQLite: INTEGER PRIMARY KEY (rowid alias — auto-increments implicitly).
     * PostgreSQL: SERIAL PRIMARY KEY.
     * MySQL: INT AUTO_INCREMENT PRIMARY KEY.
     * Others: INTEGER PRIMARY KEY (ANSI; behaviour depends on engine).
     */
    public String primaryKeyDef(String columnName) {
        switch (this) {
            case POSTGRESQL: return columnName + " SERIAL PRIMARY KEY";
            case MYSQL:      return columnName + " INT AUTO_INCREMENT PRIMARY KEY";
            default:         return columnName + " INTEGER PRIMARY KEY";
        }
    }

    /** Maps a Java field type to a SQL type appropriate for this dialect. */
    public String sqlType(Class<?> type) {
        if (type == int.class        || type == Integer.class)         return "INTEGER";
        if (type == long.class       || type == Long.class)            return "BIGINT";
        if (type == short.class      || type == Short.class)           return "SMALLINT";
        if (type == byte.class       || type == Byte.class)            return "TINYINT";
        if (type == float.class      || type == Float.class)           return "REAL";
        if (type == double.class     || type == Double.class)          return doublePrecision();
        if (type == boolean.class    || type == Boolean.class)         return booleanType();
        if (type == BigDecimal.class)                                  return "DECIMAL";
        if (type == LocalDate.class)                                   return "DATE";
        if (type == LocalDateTime.class || type == Instant.class)      return "TIMESTAMP";
        if (type == LocalTime.class)                                   return "TIME";
        if (type == UUID.class)                                        return uuidType();
        if (type == byte[].class)                                      return blobType();
        if (type.isEnum())                                             return "TEXT";
        return "TEXT";
    }

    // SQLite has no native BOOLEAN column type — uses INTEGER (0/1).
    private String booleanType() {
        return this == SQLITE ? "INTEGER" : "BOOLEAN";
    }

    // PostgreSQL uses DOUBLE PRECISION; most others accept DOUBLE.
    private String doublePrecision() {
        return this == POSTGRESQL ? "DOUBLE PRECISION" : "DOUBLE";
    }

    // PostgreSQL has a native UUID type; others store as TEXT.
    private String uuidType() {
        return this == POSTGRESQL ? "UUID" : "TEXT";
    }

    // PostgreSQL uses BYTEA; most others use BLOB.
    private String blobType() {
        return this == POSTGRESQL ? "BYTEA" : "BLOB";
    }
}
