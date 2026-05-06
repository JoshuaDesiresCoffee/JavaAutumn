package io.github.finch.core.query;

import java.util.List;

public interface SelectQuery<T> {
    /** Adds a WHERE clause with {@code ?} positional parameters. */
    SelectQuery<T> WHERE(String condition, Object... params);

    /** Adds WHERE conditions from non-null fields of {@code example}, joined with AND. */
    SelectQuery<T> WHERE(Object example);

    /** Appends a raw, unparameterized SQL fragment to the WHERE clause. Never use with user input. */
    SelectQuery<T> whereRaw(String rawSql);

    /** Eagerly loads the given related type via a batched IN query — no N+1. */
    SelectQuery<T> JOIN(Class<?> related);

    /** Sets the ORDER BY clause, e.g. {@code "created_at DESC"}. */
    SelectQuery<T> ORDER_BY(String orderByClause);

    /** Limits the number of rows returned. */
    SelectQuery<T> LIMIT(int n);

    /** Skips the first {@code n} rows. Requires a preceding LIMIT on databases that mandate it. */
    SelectQuery<T> OFFSET(int n);

    /** Executes the query and returns the matching rows. */
    List<T> EXEC();
}
