package io.github.finch.core.query;

import io.github.finch.core.mapping.EntityMapper;
import io.github.finch.core.pool.ConnectionPool;

import java.sql.*;
import java.util.Arrays;

public class DeleteQueryImpl<T> extends BaseQuery<T> implements DeleteQuery<T> {

    public DeleteQueryImpl(ConnectionPool pool, Class<T> tableClass) {
        super(pool);
        this.tableClass = tableClass;
    }

    @Override public DeleteQuery<T> WHERE(String condition, Object... params) {
        setWhere(condition, params); return this;
    }
    @Override public DeleteQuery<T> WHERE(Object example) {
        setWhereFromObject(example); return this;
    }
    @Override public DeleteQuery<T> whereRaw(String raw) {
        setWhereRaw(raw); return this;
    }

    @Override
    public void EXEC() {
        String where = whereFragment();
        String sql = "DELETE FROM " + tableName()
                + (where != null ? " WHERE " + where : "");

        Connection conn = null;
        try {
            conn = pool.borrow();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                applyParams(ps, 1);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("DELETE failed [" + tableClass.getSimpleName() + "]: "
                    + sql + " params=" + Arrays.toString(whereParams), e);
        } finally {
            if (conn != null) pool.release(conn);
        }
    }
}
