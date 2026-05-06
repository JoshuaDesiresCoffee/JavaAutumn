package io.github.finch.core.query;

import io.github.finch.core.pool.ConnectionPool;

public class InsertIntoImpl<T> implements InsertInto<T> {

    private final ConnectionPool pool;
    private final Class<T>       tableClass;

    public InsertIntoImpl(ConnectionPool pool, Class<T> tableClass) {
        this.pool       = pool;
        this.tableClass = tableClass;
    }

    @Override
    public InsertQuery<T> VALUES(T obj) {
        return new InsertQueryImpl<>(pool, obj);
    }
}
