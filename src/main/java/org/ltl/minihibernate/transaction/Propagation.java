package org.ltl.minihibernate.transaction;

/**
 * Transaction propagation behavior.
 * Modeled after Spring's Propagation enum.
 */
public enum Propagation {
  
  /**
   * Support current transaction; create new one if none exists.
   * This is the default.
   */
  REQUIRED,
  
  /**
   * Create a new transaction, suspending current if exists.
   */
  REQUIRES_NEW,
  
  /**
   * Support current transaction; execute non-transactionally if none exists.
   */
  SUPPORTS,
  
  /**
   * Execute non-transactionally, suspend current if exists.
   */
  NOT_SUPPORTED,
  
  /**
   * Support current transaction; throw exception if none exists.
   */
  MANDATORY,
  
  /**
   * Execute non-transactionally; throw exception if transaction exists.
   */
  NEVER
}
