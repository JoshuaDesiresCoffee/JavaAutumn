package io.github.finch.core.pool;

import javax.sql.DataSource;
import java.sql.*;

/**
 * Adapter so any javax.sql.DataSource (e.g. HikariCP) can be used as the pool.
 * Connection management (pooling, validation) is delegated to the DataSource.
 */
public class DataSourcePool implements ConnectionPool {

    private final DataSource ds;

    public DataSourcePool(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public Connection borrow() throws SQLException {
        return ds.getConnection();
    }

    @Override
    public void release(Connection conn) {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
    }

    @Override
    public void close() {}
}
