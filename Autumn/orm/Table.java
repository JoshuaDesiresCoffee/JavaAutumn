package io.github.finch.core;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
/** Marks a class as a database entity managed by Finch. */
public @interface Table {
    /** Overrides the table name. Defaults to the lowercase class name. */
    String name() default "";
}