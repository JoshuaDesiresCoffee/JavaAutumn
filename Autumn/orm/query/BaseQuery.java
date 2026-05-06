package io.github.finch.core.query;

import io.github.finch.core.mapping.EntityMapper;
import io.github.finch.core.mapping.EntityMapper.FieldInfo;
import io.github.finch.core.pool.ConnectionPool;

import java.sql.*;
import java.time.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

abstract class BaseQuery<T> {

    protected final ConnectionPool pool;
    protected Class<T>   tableClass;
    protected T          object;
    protected String     whereTemplate; // parameterized: "id = ?"
    protected Object[]   whereParams;
    protected String     whereRawSql;   // literal SQL, user's responsibility

    BaseQuery(ConnectionPool pool) {
        this.pool = pool;
    }

    // ── WHERE helpers ─────────────────────────────────────────────────────────

    protected void setWhere(String template, Object[] params) {
        this.whereTemplate = template;
        this.whereParams   = params;
        this.whereRawSql   = null;
    }

    protected void setWhereRaw(String raw) {
        this.whereRawSql   = raw;
        this.whereTemplate = null;
        this.whereParams   = null;
    }

    // Builds a parameterized WHERE from an object's non-null fields.
    protected void setWhereFromObject(Object o) {
        List<FieldInfo> fields = EntityMapper.getFields(o.getClass());
        StringJoiner template = new StringJoiner(" AND ");
        List<Object> params = new ArrayList<>();
        for (FieldInfo fi : fields) {
            if (fi.columnName == null) continue;
            try {
                Object val = fi.field.get(o);
                if (val == null) continue;
                template.add(fi.columnName + " = ?");
                if (fi.isForeignKey) {
                    FieldInfo relId = EntityMapper.getIdField(fi.relatedType);
                    params.add(relId.field.get(val));
                } else {
                    params.add(val);
                }
            } catch (IllegalAccessException e) { throw new RuntimeException(e); }
        }
        this.whereTemplate = template.toString();
        this.whereParams   = params.toArray();
        this.whereRawSql   = null;
    }

    // Returns the effective WHERE fragment (without the "WHERE" keyword)
    protected String whereFragment() {
        if (whereTemplate != null) return whereTemplate;
        if (whereRawSql   != null) return whereRawSql;
        return null;
    }

    protected int applyParams(PreparedStatement ps, int paramIndex) throws SQLException {
        if (whereParams != null) {
            for (Object p : whereParams) ps.setObject(paramIndex++, toJdbcValue(p));
        }
        return paramIndex;
    }

    protected static Object toJdbcValue(Object val) {
        if (val == null)                    return null;
        if (val instanceof LocalDate)       return java.sql.Date.valueOf((LocalDate) val);
        if (val instanceof LocalDateTime)   return java.sql.Timestamp.valueOf((LocalDateTime) val);
        if (val instanceof LocalTime)       return java.sql.Time.valueOf((LocalTime) val);
        if (val instanceof Instant)         return java.sql.Timestamp.from((Instant) val);
        if (val instanceof Enum<?>)         return ((Enum<?>) val).name();
        return val;
    }

    // ── Execution helpers ─────────────────────────────────────────────────────

    protected void execUpdate(String sql, Object[] valueParams) {
        Connection conn = null;
        try {
            conn = pool.borrow();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int idx = 1;
                if (valueParams != null) {
                    for (Object v : valueParams) ps.setObject(idx++, toJdbcValue(v));
                }
                applyParams(ps, idx);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Query failed [" + (tableClass != null ? tableClass.getSimpleName() : "?") + "]: "
                    + sql + " valueParams=" + Arrays.toString(valueParams) + " whereParams=" + Arrays.toString(whereParams), e);
        } finally {
            if (conn != null) pool.release(conn);
        }
    }

    protected String tableName() {
        return EntityMapper.tableName(tableClass);
    }

    protected static <C> C instantiate(Class<C> cls) {
        try {
            return cls.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(cls.getName() + " must have a public no-arg constructor", e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to instantiate " + cls.getName(), e);
        }
    }
}
