package com.minihibernate.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to define custom queries on repository methods.
 * 
 * Similar to Spring Data JPA's @Query annotation.
 * 
 * Usage:
 * <pre>
 * public interface UserRepository extends MiniRepository<User, Long> {
 *     
 *     @Query("SELECT * FROM users WHERE email = ?1")
 *     User findByEmail(String email);
 *     
 *     @Query("SELECT * FROM users WHERE name LIKE ?1")
 *     List<User> findByNameContaining(String pattern);
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Query {
    
    /**
     * The SQL query to execute.
     * Use ?1, ?2, etc. for positional parameters.
     */
    String value();
    
    /**
     * Whether this is a native SQL query (true) or JPQL-like (false).
     * For mini-hibernate, we only support native SQL for now.
     */
    boolean nativeQuery() default true;
}
