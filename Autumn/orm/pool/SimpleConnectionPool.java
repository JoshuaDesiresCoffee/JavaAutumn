package io.github.finch.core.pool;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class SimpleConnectionPool implements ConnectionPool {

    private final String url;
    private final String user;
    private final String password;
    private final int maxSize;
    private final BlockingQueue<Connection> idle;
    private final List<Connection> all;

    public SimpleConnectionPool(String url, String user, String password, int maxSize) throws SQLException {
        this.url     = url;
        this.user    = user;
        this.password = password;
        this.maxSize = maxSize;
        this.idle = new ArrayBlockingQueue<>(maxSize);
        this.all  = new ArrayList<>(maxSize);

        int initial = Math.max(1, maxSize / 4);
        for (int i = 0; i < initial; i++) {
            Connection c = newConnection();
            idle.offer(c);
            all.add(c);
        }
    }

    @Override
    public Connection borrow() throws SQLException {
        Connection conn = idle.poll();
        if (conn != null) {
            if (isValid(conn)) return conn;
            discard(conn);
        }

        synchronized (this) {
            if (all.size() < maxSize) {
                conn = newConnection();
                all.add(conn);
                return conn;
            }
        }

        try {
            conn = idle.poll(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted waiting for connection");
        }
        if (conn == null) throw new SQLException("Connection pool exhausted (max=" + maxSize + ")");
        if (!isValid(conn)) {
            discard(conn);
            conn = newConnection();
            synchronized (this) { all.add(conn); }
        }
        return conn;
    }

    private synchronized void discard(Connection conn) {
        all.remove(conn);
        try { conn.close(); } catch (SQLException ignored) {}
    }

    @Override
    public void release(Connection conn) {
        if (conn != null) idle.offer(conn);
    }

    @Override
    public void close() {
        for (Connection c : all) {
            try { c.close(); } catch (SQLException ignored) {}
        }
        all.clear();
        idle.clear();
    }

    private Connection newConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private boolean isValid(Connection c) {
        try { return c != null && !c.isClosed() && c.isValid(1); }
        catch (SQLException e) { return false; }
    }
}
