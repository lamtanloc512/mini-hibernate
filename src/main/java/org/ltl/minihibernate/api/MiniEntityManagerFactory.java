package org.ltl.minihibernate.api;

import jakarta.persistence.EntityManagerFactory;
import java.io.Closeable;

import jakarta.persistence.Query;

/**
 * MiniEntityManagerFactory - Factory interface.
 * 
 * Extends JPA's EntityManagerFactory interface.
 */
public interface MiniEntityManagerFactory extends EntityManagerFactory, Closeable {

  /**
   * Creates a new EntityManager.
   */
  MiniEntityManager createEntityManager();

  /**
   * Checks if the factory is still open.
   */
  boolean isOpen();

  void addNamedQuery(String name, Query query);
}
