package io.github.finch.core.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
/**
 * Marks a {@code List<T>} field as a many-to-many relationship.
 * A junction table is created automatically by {@code tables(...)} using alphabetical name ordering
 * ({@code alpha_beta}). Junction rows must currently be managed manually via {@code db.EXEC}.
 * The list is populated only when {@code JOIN(T.class)} is included in the query.
 */
public @interface ManyToMany {}
