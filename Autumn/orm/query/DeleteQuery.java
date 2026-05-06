package io.github.finch.core.query;

public interface DeleteQuery<T> {
    /** Adds a WHERE clause with {@code ?} positional parameters. */
    DeleteQuery<T> WHERE(String condition, Object... params);

    /** Adds WHERE conditions from non-null fields of {@code example}, joined with AND. */
    DeleteQuery<T> WHERE(Object example);

    /** Appends a raw, unparameterized SQL fragment to the WHERE clause. Never use with user input. */
    DeleteQuery<T> whereRaw(String rawSql);

    /** Executes the DELETE statement. Omitting WHERE deletes all rows. */
    void EXEC();
}
