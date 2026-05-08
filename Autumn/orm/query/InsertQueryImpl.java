package Autumn.orm.query;

import Autumn.orm.mapping.EntityMapper;
import Autumn.orm.mapping.EntityMapper.FieldInfo;
import Autumn.orm.pool.ConnectionPool;

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
            try {
                Object val = fi.field.get(object);
                // Skip id when 0/null — let the DB auto-generate it
                if (fi.isId) {
                    if (val == null) continue;
                    if (val instanceof Number && ((Number) val).longValue() == 0L) continue;
                }
                cols.add(fi.columnName);
                placeholders.add("?");
                if (fi.isForeignKey && val != null) {
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
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 0; i < values.size(); i++) ps.setObject(i + 1, toJdbcValue(values.get(i)));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        FieldInfo idField = EntityMapper.getIdField(tableClass);
                        try {
                            idField.field.set(object, SelectQueryImpl.coerce(keys.getObject(1), idField.field.getType()));
                        } catch (IllegalAccessException e) { throw new RuntimeException(e); }
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("INSERT failed [" + tableClass.getSimpleName() + "]: " + sql + " values=" + values, e);
        } finally {
            if (conn != null) pool.release(conn);
        }
    }
}
