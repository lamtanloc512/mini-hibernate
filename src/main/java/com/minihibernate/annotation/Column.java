package com.minihibernate.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as a database column.
 * 
 * Similar to javax.persistence.Column / jakarta.persistence.Column
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {
    
    /**
     * The name of the column in the database.
     * If not specified, defaults to the field name.
     */
    String name() default "";
    
    /**
     * Whether the column allows null values.
     */
    boolean nullable() default true;
    
    /**
     * The column length for string types.
     */
    int length() default 255;
    
    /**
     * Whether this column is unique.
     */
    boolean unique() default false;
}
