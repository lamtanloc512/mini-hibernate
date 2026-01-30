package org.ltl.minihibernate.api;

import jakarta.persistence.TypedQuery;

/**
 * MiniTypedQuery - Type-safe query interface.
 * 
 * Extends JPA's TypedQuery interface for compatibility.
 */
public interface MiniTypedQuery<T> extends TypedQuery<T> {

  MiniTypedQuery<T> where(String field, Object value);

  @Override
  MiniTypedQuery<T> setMaxResults(int maxResult);

  @Override
  MiniTypedQuery<T> setFirstResult(int startPosition);
}
