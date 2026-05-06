package io.github.finch.core.query;

import io.github.finch.core.mapping.EntityMapper;
import io.github.finch.core.mapping.EntityMapper.FieldInfo;
import io.github.finch.core.pool.ConnectionPool;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

public class SelectQueryImpl<T> extends BaseQuery<T> implements SelectQuery<T> {

    private int    limit   = -1;
    private int    offset  = -1;
    private String orderBy = null;
    private final List<Class<?>> joins = new ArrayList<>();

    public SelectQueryImpl(ConnectionPool pool, Class<T> tableClass) {
        super(pool);
        this.tableClass = tableClass;
    }

    @Override public SelectQuery<T> WHERE(String condition, Object... params) {
        setWhere(condition, params); return this;
    }
    @Override public SelectQuery<T> WHERE(Object example) {
        setWhereFromObject(example); return this;
    }
    @Override public SelectQuery<T> whereRaw(String raw) {
        setWhereRaw(raw); return this;
    }
    @Override public SelectQuery<T> JOIN(Class<?> related) {
        joins.add(related); return this;
    }
    // Allows: column names, optional table prefix, optional ASC/DESC, comma-separated.
    // Rejects anything else (quotes, semicolons, subqueries, etc.).
    private static final java.util.regex.Pattern SAFE_ORDER_BY =
            java.util.regex.Pattern.compile(
                    "^[a-zA-Z_][a-zA-Z0-9_.]*(?:\\s+(?:ASC|DESC))?" +
                    "(?:\\s*,\\s*[a-zA-Z_][a-zA-Z0-9_.]*(?:\\s+(?:ASC|DESC))?)*$",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    @Override public SelectQuery<T> ORDER_BY(String clause) {
        if (clause == null || !SAFE_ORDER_BY.matcher(clause.trim()).matches())
            throw new IllegalArgumentException("ORDER_BY clause contains unsafe characters: " + clause);
        orderBy = clause; return this;
    }
    @Override public SelectQuery<T> LIMIT(int n) {
        limit = n; return this;
    }
    @Override public SelectQuery<T> OFFSET(int n) {
        offset = n; return this;
    }

    @Override
    public List<T> EXEC() {
        String where = whereFragment();
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName());
        if (where != null)              sql.append(" WHERE ").append(where);
        if (orderBy != null)            sql.append(" ORDER BY ").append(orderBy);
        if (limit > 0)                   sql.append(" LIMIT ").append(limit);
        if (offset >= 0)                sql.append(" OFFSET ").append(offset);

        List<T> results = new ArrayList<>();
        Connection conn = null;
        try {
            conn = pool.borrow();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                applyParams(ps, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(hydrate(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("SELECT failed [" + tableClass.getSimpleName() + "]: "
                    + sql + " params=" + Arrays.toString(whereParams), e);
        } finally {
            if (conn != null) pool.release(conn);
        }

        if (!results.isEmpty()) loadRelations(results);
        return results;
    }

    // ── Hydration ─────────────────────────────────────────────────────────────

    private T hydrate(ResultSet rs) throws Exception {
        T obj = instantiate(tableClass);
        for (FieldInfo fi : EntityMapper.getFields(tableClass)) {
            if (fi.columnName == null) continue; // OneToMany / ManyToMany — handled later
            if (fi.isForeignKey) {
                // Create an unhydrated stub containing only the id
                Object fkId = rs.getObject(fi.columnName);
                if (fkId != null) {
                    Object stub = instantiate(fi.relatedType);
                    FieldInfo idField = EntityMapper.getIdField(fi.relatedType);
                    idField.field.set(stub, coerce(fkId, idField.field.getType()));
                    fi.field.set(obj, stub);
                }
            } else {
                Object val = rs.getObject(fi.columnName);
                if (val != null) fi.field.set(obj, coerce(val, fi.field.getType()));
            }
        }
        return obj;
    }

    // ── Relation loading ──────────────────────────────────────────────────────

    private void loadRelations(List<T> results) {
        List<FieldInfo> fields = EntityMapper.getFields(tableClass);

        // Collect parent IDs once
        FieldInfo idField = EntityMapper.getIdField(tableClass);
        List<Object> parentIds = results.stream()
                .map(r -> { try { return idField.field.get(r); }
                            catch (Exception e) { throw new RuntimeException(e); } })
                .collect(Collectors.toList());

        String inClause = parentIds.stream().map(id -> "?").collect(Collectors.joining(", "));

        for (FieldInfo fi : fields) {
            if (!joins.contains(fi.relatedType)) continue;

            if (fi.isForeignKey) {
                // Many-to-one: fully hydrate stubs already placed
                fullyHydrateFkField(results, fi, parentIds);

            } else if (fi.isOneToMany && fi.relatedType != null) {
                loadOneToMany(results, fi, parentIds, inClause);

            } else if (fi.isManyToMany && fi.relatedType != null) {
                loadManyToMany(results, fi, parentIds, inClause);
            }
        }
    }

    /** Replace stub objects with fully loaded related entities. */
    private void fullyHydrateFkField(List<T> results, FieldInfo fi, List<Object> parentIds) {
        // Collect distinct FK ids from the stubs
        Set<Object> fkIds = new HashSet<>();
        for (T r : results) {
            try {
                Object stub = fi.field.get(r);
                if (stub != null) {
                    FieldInfo relId = EntityMapper.getIdField(fi.relatedType);
                    fkIds.add(relId.field.get(stub));
                }
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        if (fkIds.isEmpty()) return;

        String inClause = fkIds.stream().map(x -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT * FROM " + EntityMapper.tableName(fi.relatedType)
                + " WHERE " + EntityMapper.getIdField(fi.relatedType).columnName + " IN (" + inClause + ")";

        Map<Object, Object> byId = new HashMap<>();
        Connection conn = null;
        try {
            conn = pool.borrow();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int i = 1;
                for (Object id : fkIds) ps.setObject(i++, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Object rel = hydrateGeneric(rs, fi.relatedType);
                        FieldInfo relId = EntityMapper.getIdField(fi.relatedType);
                        byId.put(relId.field.get(rel), rel);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("JOIN hydration failed", e);
        } finally {
            if (conn != null) pool.release(conn);
        }

        for (T r : results) {
            try {
                Object stub = fi.field.get(r);
                if (stub != null) {
                    FieldInfo relId = EntityMapper.getIdField(fi.relatedType);
                    Object full = byId.get(relId.field.get(stub));
                    if (full != null) fi.field.set(r, full);
                }
            } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    /** Load child rows where child.{parentTable}_id IN (parentIds). */
    private void loadOneToMany(List<T> results, FieldInfo fi, List<Object> parentIds, String inClause) {
        // Find the FK column in the child pointing back to this table
        String fkCol = findFkColumnInChild(fi.relatedType, tableClass);
        String sql = "SELECT * FROM " + EntityMapper.tableName(fi.relatedType)
                + " WHERE " + fkCol + " IN (" + inClause + ")";

        Map<Object, List<Object>> byParentId = new HashMap<>();
        Connection conn = null;
        try {
            conn = pool.borrow();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int i = 1;
                for (Object id : parentIds) ps.setObject(i++, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Object child = hydrateGeneric(rs, fi.relatedType);
                        Object parentIdVal = rs.getObject(fkCol);
                        byParentId.computeIfAbsent(parentIdVal, k -> new ArrayList<>()).add(child);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("OneToMany load failed", e);
        } finally {
            if (conn != null) pool.release(conn);
        }

        FieldInfo idField = EntityMapper.getIdField(tableClass);
        for (T r : results) {
            try {
                Object parentId = idField.field.get(r);
                List<Object> children = byParentId.getOrDefault(parentId, Collections.emptyList());
                fi.field.set(r, children);
            } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    /** Load related rows via junction table. */
    private void loadManyToMany(List<T> results, FieldInfo fi, List<Object> parentIds, String inClause) {
        String tableA    = EntityMapper.tableName(tableClass);
        String tableB    = EntityMapper.tableName(fi.relatedType);
        String[] sorted  = {tableA, tableB};
        Arrays.sort(sorted);
        String junction  = sorted[0] + "_" + sorted[1];
        String colA      = tableA + "_id";
        String colB      = tableB + "_id";
        String idColB    = EntityMapper.getIdField(fi.relatedType).columnName;

        String sql = "SELECT b.*, j." + colA + " AS __parent_id FROM "
                + tableB + " b JOIN " + junction + " j ON b." + idColB + " = j." + colB
                + " WHERE j." + colA + " IN (" + inClause + ")";

        Map<Object, List<Object>> byParentId = new HashMap<>();
        Connection conn = null;
        try {
            conn = pool.borrow();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int i = 1;
                for (Object id : parentIds) ps.setObject(i++, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Object child    = hydrateGeneric(rs, fi.relatedType);
                        Object parentId = rs.getObject("__parent_id");
                        byParentId.computeIfAbsent(parentId, k -> new ArrayList<>()).add(child);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("ManyToMany load failed", e);
        } finally {
            if (conn != null) pool.release(conn);
        }

        FieldInfo idField = EntityMapper.getIdField(tableClass);
        for (T r : results) {
            try {
                Object parentId = idField.field.get(r);
                List<Object> related = byParentId.getOrDefault(parentId, Collections.emptyList());
                fi.field.set(r, related);
            } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static Object hydrateGeneric(ResultSet rs, Class<?> cls) throws Exception {
        Object obj = instantiate(cls);
        for (FieldInfo fi : EntityMapper.getFields(cls)) {
            if (fi.columnName == null || fi.isOneToMany || fi.isManyToMany) continue;
            Object val = rs.getObject(fi.columnName);
            if (val != null) fi.field.set(obj, coerce(val, fi.field.getType()));
        }
        return obj;
    }

    /** Finds the column name in childClass that is a FK back to parentClass. */
    private static String findFkColumnInChild(Class<?> childClass, Class<?> parentClass) {
        return EntityMapper.getFields(childClass).stream()
                .filter(fi -> fi.isForeignKey && fi.relatedType == parentClass)
                .map(fi -> fi.columnName)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        childClass.getSimpleName() + " has no FK field pointing to " + parentClass.getSimpleName()));
    }

    static Object coerce(Object val, Class<?> target) {
        if (val == null) return null;
        if (target.isInstance(val)) return val;
        String s = val.toString().trim();
        if (target == int.class     || target == Integer.class)   return Integer.parseInt(s);
        if (target == long.class    || target == Long.class)      return Long.parseLong(s);
        if (target == short.class   || target == Short.class)     return Short.parseShort(s);
        if (target == byte.class    || target == Byte.class)      return Byte.parseByte(s);
        if (target == float.class   || target == Float.class)     return Float.parseFloat(s);
        if (target == double.class  || target == Double.class)    return Double.parseDouble(s);
        if (target == boolean.class || target == Boolean.class)   return Boolean.parseBoolean(s);
        if (target == BigDecimal.class)                           return new BigDecimal(s);
        if (target == UUID.class)                                 return UUID.fromString(s);
        if (target == LocalDate.class) {
            if (val instanceof java.sql.Date)       return ((java.sql.Date) val).toLocalDate();
            if (val instanceof java.sql.Timestamp)  return ((java.sql.Timestamp) val).toLocalDateTime().toLocalDate();
            return LocalDate.parse(s);
        }
        if (target == LocalDateTime.class) {
            if (val instanceof java.sql.Timestamp)  return ((java.sql.Timestamp) val).toLocalDateTime();
            if (val instanceof java.sql.Date)       return ((java.sql.Date) val).toLocalDate().atStartOfDay();
            return LocalDateTime.parse(s.replace(" ", "T")); // SQLite stores as "YYYY-MM-DD HH:MM:SS"
        }
        if (target == LocalTime.class) {
            if (val instanceof java.sql.Time) return ((java.sql.Time) val).toLocalTime();
            return LocalTime.parse(s);
        }
        if (target == Instant.class) {
            if (val instanceof java.sql.Timestamp) return ((java.sql.Timestamp) val).toInstant();
            if (val instanceof Long)               return Instant.ofEpochMilli((Long) val);
            return Instant.parse(s);
        }
        if (target.isEnum()) {
            @SuppressWarnings("unchecked")
            Class<Enum> enumType = (Class<Enum>) target;
            return Enum.valueOf(enumType, s);
        }
        return val;
    }
}
