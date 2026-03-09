package com.toonformat.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the TOON key name for a field.
 *
 * <pre>
 * public class User {
 *     &#064;ToonField("user_name")
 *     private String userName;
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ToonField {
    String value();
}
