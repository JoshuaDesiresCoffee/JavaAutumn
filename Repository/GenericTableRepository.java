package Repository;

import Service.db.Database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GenericTableRepository {

    public List<String> listTables() throws SQLException {
        try (Connection connection = Database.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            List<String> tables = new ArrayList<>();

            try (ResultSet resultSet = metadata.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString("TABLE_NAME");
                    if (tableName != null && !tableName.startsWith("sqlite_")) {
                        tables.add(tableName);
                    }
                }
            }

            tables.sort(String::compareToIgnoreCase);
            return tables;
        }
    }

    public List<ColumnMeta> getColumns(String tableName) throws SQLException {
        String normalizedTableName = requireKnownTable(tableName);

        try (Connection connection = Database.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            Set<String> primaryKeys = new HashSet<>();

            try (ResultSet pkResult = metadata.getPrimaryKeys(null, null, normalizedTableName)) {
                while (pkResult.next()) {
                    primaryKeys.add(pkResult.getString("COLUMN_NAME"));
                }
            }

            List<ColumnMeta> columns = new ArrayList<>();
            try (ResultSet columnResult = metadata.getColumns(null, null, normalizedTableName, "%")) {
                while (columnResult.next()) {
                    String name = columnResult.getString("COLUMN_NAME");
                    int jdbcType = columnResult.getInt("DATA_TYPE");
                    String typeName = columnResult.getString("TYPE_NAME");
                    boolean nullable = columnResult.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
                    boolean autoIncrement = "YES".equalsIgnoreCase(columnResult.getString("IS_AUTOINCREMENT"));
                    boolean primaryKey = primaryKeys.contains(name);

                    columns.add(new ColumnMeta(name, jdbcType, typeName, nullable, autoIncrement, primaryKey));
                }
            }

            return columns;
        }
    }

    public List<Map<String, Object>> getRows(String tableName) throws SQLException {
        String normalizedTableName = requireKnownTable(tableName);
        List<ColumnMeta> columns = getColumns(normalizedTableName);
        String sql = "SELECT * FROM " + quoteIdentifier(normalizedTableName);

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<Map<String, Object>> rows = new ArrayList<>();

            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (ColumnMeta column : columns) {
                    row.put(column.name(), resultSet.getObject(column.name()));
                }
                rows.add(row);
            }

            return rows;
        }
    }

    public void insertRow(String tableName, Map<String, String> values) throws SQLException {
        String normalizedTableName = requireKnownTable(tableName);
        List<ColumnMeta> columns = getColumns(normalizedTableName);
        List<ColumnMeta> writable = columns.stream()
                .filter(column -> !column.autoIncrement())
                .toList();

        List<ColumnMeta> includedColumns = writable.stream()
                .filter(column -> values.containsKey(column.name()))
                .toList();

        if (includedColumns.isEmpty()) {
            String sql = "INSERT INTO " + quoteIdentifier(normalizedTableName) + " DEFAULT VALUES";
            try (Connection connection = Database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            }
            return;
        }

        String columnSql = includedColumns.stream()
                .map(column -> quoteIdentifier(column.name()))
                .collect(Collectors.joining(", "));
        String placeholders = includedColumns.stream()
                .map(column -> "?")
                .collect(Collectors.joining(", "));

        String sql = "INSERT INTO " + quoteIdentifier(normalizedTableName)
                + " (" + columnSql + ") VALUES (" + placeholders + ")";

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < includedColumns.size(); i++) {
                ColumnMeta column = includedColumns.get(i);
                String rawValue = values.get(column.name());
                bindTypedValue(statement, i + 1, column, rawValue);
            }
            statement.executeUpdate();
        }
    }

    public void updateRow(String tableName, String idValue, Map<String, String> values) throws SQLException {
        String normalizedTableName = requireKnownTable(tableName);
        List<ColumnMeta> columns = getColumns(normalizedTableName);
        ColumnMeta primaryKey = getPrimaryKey(columns);

        List<ColumnMeta> updatable = columns.stream()
                .filter(column -> !column.primaryKey())
                .filter(column -> values.containsKey(column.name()))
                .toList();

        if (updatable.isEmpty()) {
            return;
        }

        String setPart = updatable.stream()
                .map(column -> quoteIdentifier(column.name()) + " = ?")
                .collect(Collectors.joining(", "));

        String sql = "UPDATE " + quoteIdentifier(normalizedTableName)
                + " SET " + setPart
                + " WHERE " + quoteIdentifier(primaryKey.name()) + " = ?";

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (ColumnMeta column : updatable) {
                bindTypedValue(statement, index, column, values.get(column.name()));
                index++;
            }

            bindTypedValue(statement, index, primaryKey, idValue);
            statement.executeUpdate();
        }
    }

    public void deleteRow(String tableName, String idValue) throws SQLException {
        String normalizedTableName = requireKnownTable(tableName);
        List<ColumnMeta> columns = getColumns(normalizedTableName);
        ColumnMeta primaryKey = getPrimaryKey(columns);

        String sql = "DELETE FROM " + quoteIdentifier(normalizedTableName)
                + " WHERE " + quoteIdentifier(primaryKey.name()) + " = ?";

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindTypedValue(statement, 1, primaryKey, idValue);
            statement.executeUpdate();
        }
    }

    public String requireKnownTable(String tableName) throws SQLException {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Missing table name.");
        }

        List<String> tables = listTables();
        for (String knownTable : tables) {
            if (knownTable.equalsIgnoreCase(tableName.trim())) {
                return knownTable;
            }
        }

        throw new IllegalArgumentException("Unknown table: " + tableName);
    }

    public ColumnMeta getPrimaryKey(List<ColumnMeta> columns) {
        return columns.stream()
                .filter(ColumnMeta::primaryKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Table needs a primary key for update/delete."));
    }

    private String quoteIdentifier(String identifier) {
        String trimmed = identifier == null ? "" : identifier.trim();
        if (!trimmed.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return "\"" + trimmed + "\"";
    }

    private void bindTypedValue(PreparedStatement statement, int parameterIndex, ColumnMeta column, String rawValue) throws SQLException {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            if (!column.nullable()) {
                throw new IllegalArgumentException("Missing value for required column: " + column.name());
            }
            statement.setNull(parameterIndex, column.jdbcType());
            return;
        }

        int jdbcType = column.jdbcType();
        switch (jdbcType) {
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT, Types.BIGINT -> statement.setLong(parameterIndex, parseLong(value, column.name()));
            case Types.FLOAT, Types.REAL, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> statement.setDouble(parameterIndex, parseDouble(value, column.name()));
            case Types.BOOLEAN, Types.BIT -> statement.setBoolean(parameterIndex, parseBoolean(value, column.name()));
            default -> statement.setString(parameterIndex, value);
        }
    }

    private long parseLong(String value, String columnName) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer for column " + columnName + ": " + value);
        }
    }

    private double parseDouble(String value, String columnName) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid decimal for column " + columnName + ": " + value);
        }
    }

    private boolean parseBoolean(String value, String columnName) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean for column " + columnName + ": " + value);
    }

    public record ColumnMeta(
            String name,
            int jdbcType,
            String typeName,
            boolean nullable,
            boolean autoIncrement,
            boolean primaryKey
    ) {
    }
}
