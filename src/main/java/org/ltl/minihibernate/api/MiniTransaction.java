package org.ltl.minihibernate.api;

import jakarta.persistence.EntityTransaction;

/**
 * MiniTransaction - Transaction interface.
 * 
 * Extends JPA's EntityTransaction interface.
 */
public interface MiniTransaction extends EntityTransaction {

  void begin();

  void commit();

  void rollback();

  boolean isActive();
}
