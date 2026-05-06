package io.github.finch.core.query;

public interface UpdateSet<T> {
    /** Sets the row data from {@code obj}. FK fields are resolved to their id column. */
    UpdateQuery<T> SET(Object obj);
}
