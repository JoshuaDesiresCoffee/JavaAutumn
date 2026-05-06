package io.github.finch.core.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
/** Marks a field as the primary key. Falls back to a field named {@code id} if not present. */
public @interface Id {}
