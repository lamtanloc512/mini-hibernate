package org.ltl.minihibernate.annotation;

/**
 * Defines the types of primary key generation strategies.
 * 
 * Similar to javax.persistence.GenerationType /
 * jakarta.persistence.GenerationType
 */
public enum GenerationType {

  /**
   * Uses database identity column (auto-increment).
   */
  IDENTITY,

  /**
   * Uses a database sequence.
   */
  SEQUENCE,

  /**
   * Uses a table to generate unique values.
   */
  TABLE,

  /**
   * Let the persistence provider choose the strategy.
   */
  AUTO
}
