package io.github.finch.core.pool;

import java.sql.Connection;

public class SingleConnectionPool implements ConnectionPool {

    private final Connection conn;

    public SingleConnectionPool(Connection conn) {
        this.conn = conn;
    }

    @Override public Connection borrow() { return conn; }
    @Override public void release(Connection c) { /* no-op — transaction owns the connection */ }
    @Override public void close() { /* no-op */ }
}
