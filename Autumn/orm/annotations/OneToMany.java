package io.github.finch.core.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
/**
 * Marks a {@code List<T>} field as a one-to-many relationship.
 * The child table holds the foreign key pointing back to this entity.
 * The list is populated only when {@code JOIN(T.class)} is included in the query.
 */
public @interface OneToMany {}
