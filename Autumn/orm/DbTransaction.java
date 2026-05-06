package io.github.finch.core;

import io.github.finch.core.pool.ConnectionPool;
import io.github.finch.core.pool.SingleConnectionPool;
import io.github.finch.core.query.*;

import java.sql.*;
import java.util.*;

/**
 * A single database transaction. Holds one connection from the pool with
 * autoCommit disabled. All queries issued through this object share that
 * connection and are part of the same transaction.
 *
 * Usage — try-with-resources (recommended):
 *   try (DbTransaction t = db.TRANSACTION()) {
 *       t.UPDATE(Account.class).SET(a).WHERE("id = ?", id).EXEC();
 *       t.INSERT.INTO(Log.class).VALUES(entry).EXEC();
 *       t.COMMIT();
 *   }  // auto-ROLLBACK + connection release if COMMIT() was not called
 *
 * Usage — manual:
 *   DbTransaction t = db.TRANSACTION();
 *   t.INSERT.INTO(Post.class).VALUES(post).EXEC();
 *   t.COMMIT();  // or t.ROLLBACK()
 */
public class DbTransaction implements AutoCloseable {

    private final Connection     conn;
    private final ConnectionPool pool;    // the real pool — used only to release on close()
    private final ConnectionPool txPool;  // single-connection wrapper shared by all queries
    private boolean completed = false;

    public final Select SELECT;
    public final Insert INSERT;
    public final Delete DELETE;

    DbTransaction(Connection conn, ConnectionPool pool) throws SQLException {
        this.conn   = conn;
        this.pool   = pool;
        conn.setAutoCommit(false);
        this.txPool = new SingleConnectionPool(conn);
        this.SELECT = new Select();
        this.INSERT = new Insert();
        this.DELETE = new Delete();
    }

    // ── Dispatchers ───────────────────────────────────────────────────────────

    public final class Select {
        public <T> SelectQuery<T> FROM(Class<T> cls) {
            return new SelectQueryImpl<>(txPool, cls);
        }
    }

    public final class Insert {
        public <T> InsertInto<T> INTO(Class<T> cls) {
            return new InsertIntoImpl<>(txPool, cls);
        }
    }

    public final class Delete {
        public <T> DeleteQuery<T> FROM(Class<T> cls) {
            return new DeleteQueryImpl<>(txPool, cls);
        }
    }

    /** Begins an UPDATE statement scoped to this transaction. */
    public <T> UpdateSet<T> UPDATE(Class<T> cls) {
        return new UpdateQueryImpl<>(txPool, cls);
    }

    /**
     * Executes arbitrary SQL with positional parameters within this transaction.
     * Returns rows as column-name → value maps for SELECT; empty list for DML.
     */
    public List<Map<String, Object>> EXEC(String sql, List<Object> params) {
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
        } catch (SQLException e) {
            throw new RuntimeException("EXEC failed: " + sql, e);
        }
    }

    // ── Transaction control ───────────────────────────────────────────────────

    /** Commits the transaction. The connection is returned to the pool on {@link #close()}. */
    public void COMMIT() {
        try {
            conn.commit();
            completed = true;
        } catch (SQLException e) {
            throw new RuntimeException("COMMIT failed", e);
        }
    }

    /** Explicitly rolls back the transaction. Also called automatically by {@link #close()} if {@link #COMMIT()} was not invoked. */
    public void ROLLBACK() {
        try {
            conn.rollback();
            completed = true;
        } catch (SQLException e) {
            throw new RuntimeException("ROLLBACK failed", e);
        }
    }

    /** Auto-rollback if COMMIT() was not called, then return the connection to the pool. */
    @Override
    public void close() {
        try { if (!completed) conn.rollback(); } catch (SQLException ignored) {}
        try { conn.setAutoCommit(true); }          catch (SQLException ignored) {}
        pool.release(conn);
    }
}
