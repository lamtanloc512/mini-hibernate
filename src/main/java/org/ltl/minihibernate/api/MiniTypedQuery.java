package org.ltl.minihibernate.api;

import java.util.List;

/**
 * MiniTypedQuery - Type-safe query interface.
 * 
 * Like JPA's TypedQuery interface.
 */
public interface MiniTypedQuery<T> {

  MiniTypedQuery<T> where(String field, Object value);

  MiniTypedQuery<T> setMaxResults(int maxResult);

  MiniTypedQuery<T> setFirstResult(int startPosition);

  List<T> getResultList();

  T getSingleResult();
}
