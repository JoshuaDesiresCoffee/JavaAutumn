package io.github.finch.core.query;

public interface InsertInto<T> {
    /** Provides the entity to insert. FK fields are resolved to their id column. */
    InsertQuery<T> VALUES(T obj);
}
