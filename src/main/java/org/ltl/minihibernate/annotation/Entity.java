package org.ltl.minihibernate.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a persistent entity.
 * 
 * Similar to javax.persistence.Entity / jakarta.persistence.Entity
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Entity {
    
    /**
     * The name of the database table.
     * If not specified, defaults to the simple class name.
     */
    String table() default "";
}
