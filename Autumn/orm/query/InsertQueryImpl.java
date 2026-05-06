package io.github.finch.core.query;

import io.github.finch.core.mapping.EntityMapper;
import io.github.finch.core.mapping.EntityMapper.FieldInfo;
import io.github.finch.core.pool.ConnectionPool;

import java.sql.*;
import java.util.*;

public class InsertQueryImpl<T> extends BaseQuery<T> implements InsertQuery<T> {

    public InsertQueryImpl(ConnectionPool pool, T obj) {
        super(pool);
        this.object = obj;
        @SuppressWarnings("unchecked")
        Class<T> cls = (Class<T>) obj.getClass();
        this.tableClass = cls;
    }

    @Override
    public void EXEC() {
        List<FieldInfo> fields = EntityMapper.getFields(tableClass);
        StringJoiner cols = new StringJoiner(", ");
        StringJoiner placeholders = new StringJoiner(", ");
        List<Object> values = new ArrayList<>();

        for (FieldInfo fi : fields) {
            if (fi.columnName == null) continue; // skip relation-only fields
            cols.add(fi.columnName);
            placeholders.add("?");
            try {
                Object val = fi.field.get(object);
                if (fi.isForeignKey && val != null) {
                    // Store the related entity's id, not the object itself
                    FieldInfo relId = EntityMapper.getIdField(fi.relatedType);
                    values.add(relId.field.get(val));
                } else {
                    values.add(val);
                }
            } catch (IllegalAccessException e) { throw new RuntimeException(e); }
        }

        String sql = "INSERT INTO " + EntityMapper.tableName(tableClass)
                + " (" + cols + ") VALUES (" + placeholders + ")";

        Connection conn = null;
        try {
            conn = pool.borrow();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < values.size(); i++) ps.setObject(i + 1, toJdbcValue(values.get(i)));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("INSERT failed [" + tableClass.getSimpleName() + "]: " + sql + " values=" + values, e);
        } finally {
            if (conn != null) pool.release(conn);
        }
    }
}
