package io.github.finch.core;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
/**
 * Annotates a config class with JDBC connection details for use with {@link Db#from(Class)}.
 * Avoids constructing a {@link Db.DbConfig} manually.
 */
public @interface Database {
    /** JDBC connection URL, e.g. {@code jdbc:sqlite:data/app.db}. */
    String url();
    /** Database username. Empty string for authentication-free connections. */
    String user() default "";
    /** Database password. Empty string for authentication-free connections. */
    String password() default "";
}