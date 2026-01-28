package com.minihibernate.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies that the primary key should be auto-generated.
 * 
 * Similar to javax.persistence.GeneratedValue / jakarta.persistence.GeneratedValue
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface GeneratedValue {
    
    /**
     * The generation strategy.
     */
    GenerationType strategy() default GenerationType.IDENTITY;
}
