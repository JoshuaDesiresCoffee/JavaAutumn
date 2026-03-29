package Service.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    private static final String JDBC_URL = "jdbc:sqlite:room_management.db";

    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(JDBC_URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            // SQLite ignores FKs unless this is on for the connection.
            statement.execute("PRAGMA journal_mode = WAL");
            // Lets reads proceed while a write is pending; one writer still, but less blocking than the default journal.
            statement.execute("PRAGMA busy_timeout = 5000");
            // When another request holds the write lock, wait up to 5s instead of failing at once.
        }
        // TODO: Multi-user room booking — availability, no double-book, per-user views; extend framework (routes/repos/DB) beyond generic CRUD when needed.
        return connection;
    }

    public static void initialize() throws SQLException {
        try (Connection connection = getConnection()) {
            connection.getMetaData();
        }
    }
}
