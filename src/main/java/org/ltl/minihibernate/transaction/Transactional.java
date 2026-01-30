package org.ltl.minihibernate.transaction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or class as transactional.
 * When applied, the method will be wrapped in a transaction that:
 * - Begins before method execution
 * - Commits after successful execution
 * - Rolls back on exception
 * 
 * <pre>
 * public class UserService {
 *     @Transactional
 *     public void transferMoney(Long from, Long to, BigDecimal amount) {
 *         // This method runs in a transaction
 *     }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Transactional {
  
  /**
   * Transaction propagation behavior.
   */
  Propagation propagation() default Propagation.REQUIRED;
  
  /**
   * Whether to rollback on any exception.
   */
  boolean rollbackOnAnyException() default true;
  
  /**
   * Exception classes that should trigger rollback.
   */
  Class<? extends Throwable>[] rollbackFor() default {};
  
  /**
   * Exception classes that should NOT trigger rollback.
   */
  Class<? extends Throwable>[] noRollbackFor() default {};
}
