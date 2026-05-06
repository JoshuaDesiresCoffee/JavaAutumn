package io.github.finch.core.query;

public interface UpdateQuery<T> {
    /** Merges additional fields from {@code obj} into the SET clause (e.g. a related FK object). */
    UpdateQuery<T> SET(Object obj);

    /** Adds a WHERE clause with {@code ?} positional parameters. */
    UpdateQuery<T> WHERE(String condition, Object... params);

    /** Adds WHERE conditions from non-null fields of {@code example}, joined with AND. */
    UpdateQuery<T> WHERE(Object example);

    /** Appends a raw, unparameterized SQL fragment to the WHERE clause. Never use with user input. */
    UpdateQuery<T> whereRaw(String rawSql);

    /** Executes the UPDATE statement. */
    void EXEC();
}
