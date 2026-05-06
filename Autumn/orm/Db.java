package io.github.finch.core;

import io.github.finch.core.mapping.SchemaSync;
import io.github.finch.core.pool.ConnectionPool;
import io.github.finch.core.pool.DataSourcePool;
import io.github.finch.core.pool.SimpleConnectionPool;
import io.github.finch.core.query.*;

import javax.sql.DataSource;
import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * Entry point for Finch.
 *
 * Initialization:
 *   Db db = Db.configure()
 *               .url("jdbc:sqlite:data/app.db")
 *               .poolSize(10)
 *               .tables(User.class, Post.class)
 *               .connect();
 *
 * Annotation shortcut (reads @Database, no classpath scan):
 *   Db db = Db.from(AppConfig.class);
 *
 * Query syntax:
 *   db.SELECT.FROM(User.class).WHERE("id = ?", 1).EXEC()
 *   db.INSERT.INTO(User.class).VALUES(user).EXEC()
 *   db.UPDATE(User.class).SET(user).WHERE("id = ?", user.id).EXEC()
 *   db.UPDATE(Post.class).SET(category).WHERE("id = ?", post.id).EXEC()  // FK only
 *   db.DELETE.FROM(User.class).WHERE(user).EXEC()
 */
public class Db {

    /** Convenience reference set automatically by connect() / Db.from(). */
    public static Db instance;

    private final ConnectionPool pool;

    private Db(ConnectionPool pool) {
        this.pool = pool;
    }

    // ── Dispatchers ───────────────────────────────────────────────────────────

    public final class Select {
        public <T> SelectQuery<T> FROM(Class<T> tableClass) {
            return new SelectQueryImpl<>(pool, tableClass);
        }
    }

    public final class Insert {
        public <T> InsertInto<T> INTO(Class<T> tableClass) {
            return new InsertIntoImpl<>(pool, tableClass);
        }
    }

    public final class Delete {
        public <T> DeleteQuery<T> FROM(Class<T> tableClass) {
            return new DeleteQueryImpl<>(pool, tableClass);
        }
    }

    public final Select SELECT = new Select();
    public final Insert INSERT = new Insert();
    public final Delete DELETE = new Delete();

    /** Begins an UPDATE statement for the given entity table. */
    public <T> UpdateSet<T> UPDATE(Class<T> tableClass) {
        return new UpdateQueryImpl<>(pool, tableClass);
    }

    // ── Transactions ─────────────────────────────────────────────────────────

    /**
     * Begin a transaction. The returned DbTransaction holds one connection
     * from the pool with autoCommit disabled.
     *
     * try-with-resources (recommended — auto-rollback on exception):
     *   try (DbTransaction t = db.TRANSACTION()) {
     *       t.UPDATE(Account.class).SET(a).WHERE("id = ?", id).EXEC();
     *       t.COMMIT();
     *   }
     *
     * Manual:
     *   DbTransaction t = db.TRANSACTION();
     *   t.INSERT.INTO(Post.class).VALUES(post).EXEC();
     *   t.COMMIT();  // or t.ROLLBACK()
     */
    public DbTransaction TRANSACTION() {
        try {
            return new DbTransaction(pool.borrow(), pool);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to begin transaction", e);
        }
    }

    // ── Raw SQL ───────────────────────────────────────────────────────────────

    /**
     * Execute arbitrary SQL with positional parameters.
     * Returns rows as a list of column-name → value maps for SELECT statements.
     * Returns an empty list for INSERT / UPDATE / DELETE.
     *
     *   db.EXEC("SELECT * FROM user WHERE id = ?", List.of(1))
     *   db.EXEC("DELETE FROM session WHERE expires_at < ?", List.of(cutoff))
     */
    /**
     * Executes arbitrary SQL with positional parameters.
     * Returns rows as column-name → value maps for SELECT; empty list for DML.
     */
    public List<Map<String, Object>> EXEC(String sql, List<Object> params) {
        Connection conn = null;
        try {
            conn = pool.borrow();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (params != null) {
                    for (int i = 0; i < params.size(); i++)
                        ps.setObject(i + 1, params.get(i));
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                if (ps.execute()) {
                    try (ResultSet rs = ps.getResultSet()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int cols = meta.getColumnCount();
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (int i = 1; i <= cols; i++)
                                row.put(meta.getColumnName(i), rs.getObject(i));
                            rows.add(row);
                        }
                    }
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("EXEC failed: " + sql, e);
        } finally {
            if (conn != null) pool.release(conn);
        }
    }

    // ── Schema ────────────────────────────────────────────────────────────────

    /**
     * Creates missing tables and migrates existing ones (adds new columns).
     * Columns removed from entities are left untouched to prevent data loss.
     * Junction tables for {@code @ManyToMany} relationships are created automatically.
     */
    public Db sync(Class<?>... tables) {
        SchemaSync.sync(pool, tables);
        return this;
    }

    /**
     * Returns the full CREATE TABLE DDL for the given entity classes as a SQL string.
     * Types are selected for the active database dialect (e.g. {@code SERIAL} on
     * PostgreSQL, {@code INTEGER PRIMARY KEY} on SQLite). Junction tables for
     * {@code @ManyToMany} relationships are included and deduplicated.
     *
     *   String ddl = db.exportSchema(User.class, Post.class, Tag.class);
     */
    public String exportSchema(Class<?>... tables) {
        return SchemaSync.exportSchema(pool, tables);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Closes the connection pool and releases all held connections. */
    public void close() {
        pool.close();
    }

    // ── Initialization ────────────────────────────────────────────────────────

    /** Returns a builder for configuring and creating a {@link Db} instance. */
    public static DbConfig configure() {
        return new DbConfig();
    }

    /** Reads connection info from a @Database-annotated class. No classpath scanning. */
    public static Db from(Class<?> configClass) {
        if (!configClass.isAnnotationPresent(Database.class))
            throw new RuntimeException(configClass.getName() + " must be annotated with @Database");
        Database ann = configClass.getAnnotation(Database.class);
        return configure()
                .url(ann.url())
                .user(ann.user())
                .password(ann.password())
                .connect();
    }

    // ── Config builder ────────────────────────────────────────────────────────

    /** Fluent builder returned by {@link Db#configure()}. */
    public static final class DbConfig {

        private String     url      = "";
        private String     user     = "";
        private String     password = "";
        private int        poolSize = 10;
        private DataSource dataSource;
        private Class<?>[] tables   = new Class[0];

        /** Sets the JDBC connection URL. */
        public DbConfig url(String url)           { this.url        = url; return this; }
        /** Sets the database username. */
        public DbConfig user(String user)         { this.user       = user; return this; }
        /** Sets the database password. */
        public DbConfig password(String pw)       { this.password   = pw;  return this; }
        /** Sets the maximum connection pool size (default 10). */
        public DbConfig poolSize(int n)           { this.poolSize   = n;   return this; }
        /** Supplies an external {@link DataSource} (e.g. HikariCP). Overrides url/user/password/poolSize. */
        public DbConfig dataSource(DataSource ds) { this.dataSource = ds;  return this; }
        /** Registers entity classes and runs schema sync on {@link #connect()}. */
        public DbConfig tables(Class<?>... cls)   { this.tables     = cls; return this; }

        /** Builds the {@link Db} instance, initialises the pool, and syncs any registered tables. */
        public Db connect() {
            ensureDirectories(url);
            ConnectionPool cp;
            try {
                cp = dataSource != null
                        ? new DataSourcePool(dataSource)
                        : new SimpleConnectionPool(url, user, password, poolSize);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialise connection pool", e);
            }
            Db db = new Db(cp);
            if (tables.length > 0) db.sync(tables);
            instance = db;
            return db;
        }

        private static void ensureDirectories(String url) {
            if (url.startsWith("jdbc:sqlite:")) {
                File parent = new File(url.substring("jdbc:sqlite:".length())).getParentFile();
                if (parent != null) parent.mkdirs();
            }
        }
    }
}
