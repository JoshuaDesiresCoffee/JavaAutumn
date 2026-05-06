package io.github.finch.core.pool;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConnectionPool {
    Connection borrow() throws SQLException;
    void release(Connection conn);
    void close();
}
