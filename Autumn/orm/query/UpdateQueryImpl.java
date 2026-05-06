package io.github.finch.core.query;

import io.github.finch.core.Table;
import io.github.finch.core.mapping.EntityMapper;
import io.github.finch.core.mapping.EntityMapper.FieldInfo;
import io.github.finch.core.pool.ConnectionPool;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

public class UpdateQueryImpl<T> extends BaseQuery<T> implements UpdateSet<T>, UpdateQuery<T> {

    private final List<String> setCols   = new ArrayList<>();
    private final List<Object> setValues = new ArrayList<>();

    public UpdateQueryImpl(ConnectionPool pool, Class<T> tableClass) {
        super(pool);
        this.tableClass = tableClass;
    }

    // ── SET ───────────────────────────────────────────────────────────────────

    @Override
    public UpdateQuery<T> SET(Object obj) {
        Class<?> cls = obj.getClass();
        if (cls == tableClass) {
            collectFullSet(obj);
        } else if (cls.isAnnotationPresent(Table.class)) {
            collectFkSet(obj, cls);
        } else {
            throw new RuntimeException(
                    "SET() requires the table's own type or a @Table-annotated FK type, got: " + cls.getSimpleName());
        }
        return this;
    }

    /** Enqueue every column of the table object (FK fields resolved to their id). */
    private void collectFullSet(Object obj) {
        for (FieldInfo fi : EntityMapper.getFields(tableClass)) {
            if (fi.columnName == null) continue;
            try {
                Object val = fi.field.get(obj);
                setCols.add(fi.columnName + " = ?");
                if (fi.isForeignKey && val != null) {
                    FieldInfo relId = EntityMapper.getIdField(fi.relatedType);
                    setValues.add(relId.field.get(val));
                } else {
                    setValues.add(val);
                }
            } catch (IllegalAccessException e) { throw new RuntimeException(e); }
        }
    }

    /** Enqueue only the FK column that points to the given related object's type. */
    private void collectFkSet(Object obj, Class<?> relatedClass) {
        FieldInfo fkField = EntityMapper.getFields(tableClass).stream()
                .filter(fi -> fi.isForeignKey && fi.relatedType == relatedClass)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        tableClass.getSimpleName() + " has no FK field pointing to " + relatedClass.getSimpleName()));
        try {
            FieldInfo relId = EntityMapper.getIdField(relatedClass);
            setCols.add(fkField.columnName + " = ?");
            setValues.add(relId.field.get(obj));
        } catch (IllegalAccessException e) { throw new RuntimeException(e); }
    }

    // ── WHERE ─────────────────────────────────────────────────────────────────

    @Override public UpdateQuery<T> WHERE(String condition, Object... params) {
        setWhere(condition, params); return this;
    }
    @Override public UpdateQuery<T> WHERE(Object example) {
        setWhereFromObject(example); return this;
    }
    @Override public UpdateQuery<T> whereRaw(String raw) {
        setWhereRaw(raw); return this;
    }

    // ── EXEC ──────────────────────────────────────────────────────────────────

    @Override
    public void EXEC() {
        if (setCols.isEmpty())
            throw new RuntimeException("UPDATE requires at least one SET(...)");

        StringJoiner sets = new StringJoiner(", ");
        setCols.forEach(sets::add);

        String where = whereFragment();
        String sql   = "UPDATE " + tableName() + " SET " + sets
                + (where != null ? " WHERE " + where : "");

        Connection conn = null;
        try {
            conn = pool.borrow();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int idx = 1;
                for (Object v : setValues) ps.setObject(idx++, toJdbcValue(v));
                applyParams(ps, idx);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("UPDATE failed [" + tableClass.getSimpleName() + "]: "
                    + sql + " set=" + setValues + " whereParams=" + Arrays.toString(whereParams), e);
        } finally {
            if (conn != null) pool.release(conn);
        }
    }
}
