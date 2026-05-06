package io.github.finch.core.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
/**
 * Customizes the mapped column for an entity field.
 * Omit the annotation to accept all defaults (nullable, non-unique, column name = field name).
 */
public @interface Column {
    /** Overrides the column name. Defaults to the field name. */
    String name() default "";
    /** Whether the column allows NULL. Defaults to {@code true}. */
    boolean nullable() default true;
    /** Whether a UNIQUE constraint is added. Defaults to {@code false}. */
    boolean unique() default false;
}
