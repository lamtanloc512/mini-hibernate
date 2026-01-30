package org.ltl.minihibernate.api;

import jakarta.persistence.EntityManager;
import java.io.Closeable;

/**
 * MiniEntityManager - The "JPA-like" interface.
 * 
 * This is an extension of standard JPA EntityManager.
 */
public interface MiniEntityManager extends EntityManager, Closeable {

  /**
   * Makes a transient entity persistent.
   */
  void persist(Object entity);

  /**
   * Finds an entity by primary key.
   */
  <T> T find(Class<T> entityClass, Object primaryKey);

  /**
   * Removes a persistent entity.
   */
  void remove(Object entity);

  /**
   * Merges a detached entity.
   */
  <T> T merge(T entity);

  /**
   * Flushes all pending changes to database.
   */
  void flush();

  /**
   * Clears the persistence context.
   */
  void clear();

  /**
   * Checks if entity is managed.
   */
  boolean contains(Object entity);

  /**
   * Begins a new transaction.
   */
  MiniTransaction getTransaction();

  /**
   * Creates a query for the entity type.
   */
  <T> MiniTypedQuery<T> createQuery(Class<T> entityClass);

  /**
   * Unwraps to get the actual implementation.
   * Similar to JPA's unwrap() method.
   */
  <T> T unwrap(Class<T> cls);

  /**
   * Checks if the entity manager is open.
   */
  boolean isOpen();
}
