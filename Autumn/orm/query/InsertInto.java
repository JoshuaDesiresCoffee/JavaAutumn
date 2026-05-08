package Autumn.orm.query;

public interface InsertInto<T> {
    /** Provides the entity to insert. FK fields are resolved to their id column. */
    InsertQuery<T> VALUES(T obj);
}
