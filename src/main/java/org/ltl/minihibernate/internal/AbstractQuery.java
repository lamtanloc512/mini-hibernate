package org.ltl.minihibernate.internal;

import jakarta.persistence.CacheRetrieveMode;
import jakarta.persistence.CacheStoreMode;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Parameter;
import jakarta.persistence.TemporalType;
import jakarta.persistence.TypedQuery;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Base class for all query implementations.
 * Consolidates common JPA query functionality.
 */
public abstract class AbstractQuery<T> implements TypedQuery<T> {

  protected Integer maxResults = null;
  protected Integer firstResult = null;
  protected FlushModeType flushMode = FlushModeType.AUTO;
  protected LockModeType lockMode = LockModeType.NONE;
  protected Integer timeout = null;
  protected CacheStoreMode cacheStoreMode = CacheStoreMode.USE;
  protected CacheRetrieveMode cacheRetrieveMode = CacheRetrieveMode.USE;
  protected final Map<String, Object> hints = new HashMap<>();

  @Override
  public TypedQuery<T> setMaxResults(int maxResults) {
    this.maxResults = maxResults;
    return this;
  }

  @Override
  public int getMaxResults() {
    return maxResults != null ? maxResults : Integer.MAX_VALUE;
  }

  @Override
  public TypedQuery<T> setFirstResult(int startPosition) {
    this.firstResult = startPosition;
    return this;
  }

  @Override
  public int getFirstResult() {
    return firstResult != null ? firstResult : 0;
  }

  @Override
  public TypedQuery<T> setHint(String hintName, Object value) {
    hints.put(hintName, value);
    return this;
  }

  @Override
  public Map<String, Object> getHints() {
    return hints;
  }

  @Override
  public TypedQuery<T> setFlushMode(FlushModeType flushMode) {
    this.flushMode = flushMode;
    return this;
  }

  @Override
  public FlushModeType getFlushMode() {
    return flushMode;
  }

  @Override
  public TypedQuery<T> setLockMode(LockModeType lockMode) {
    this.lockMode = lockMode;
    return this;
  }

  @Override
  public LockModeType getLockMode() {
    return lockMode;
  }

  @Override
  public TypedQuery<T> setTimeout(Integer timeout) {
    this.timeout = timeout;
    return this;
  }

  @Override
  public Integer getTimeout() {
    return timeout;
  }

  @Override
  public TypedQuery<T> setCacheStoreMode(CacheStoreMode cacheStoreMode) {
    this.cacheStoreMode = cacheStoreMode;
    return this;
  }

  @Override
  public CacheStoreMode getCacheStoreMode() {
    return cacheStoreMode;
  }

  @Override
  public TypedQuery<T> setCacheRetrieveMode(CacheRetrieveMode cacheRetrieveMode) {
    this.cacheRetrieveMode = cacheRetrieveMode;
    return this;
  }

  @Override
  public CacheRetrieveMode getCacheRetrieveMode() {
    return cacheRetrieveMode;
  }

  @Override
  public Stream<T> getResultStream() {
    return getResultList().stream();
  }

  @Override
  public T getSingleResultOrNull() {
    java.util.List<T> results = getResultList();
    return results.isEmpty() ? null : results.get(0);
  }

  // Parameter stubs that are often overridden or throw exception by default
  @Override
  public <T1> T1 unwrap(Class<T1> cls) {
    throw new UnsupportedOperationException("Unwrap not supported");
  }

  @Override
  public abstract Set<Parameter<?>> getParameters();

  @Override
  public Parameter<?> getParameter(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T1> Parameter<T1> getParameter(String name, Class<T1> type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Parameter<?> getParameter(int position) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T1> Parameter<T1> getParameter(int position, Class<T1> type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isBound(Parameter<?> param) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T1> T1 getParameterValue(Parameter<T1> param) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getParameterValue(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getParameterValue(int position) {
    throw new UnsupportedOperationException();
  }

  @Override
  @SuppressWarnings("deprecation")
  public TypedQuery<T> setParameter(Parameter<Calendar> param, Calendar value, TemporalType temporalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  @SuppressWarnings("deprecation")
  public TypedQuery<T> setParameter(Parameter<Date> param, Date value, TemporalType temporalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  @SuppressWarnings("deprecation")
  public TypedQuery<T> setParameter(String name, Calendar value, TemporalType temporalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  @SuppressWarnings("deprecation")
  public TypedQuery<T> setParameter(String name, Date value, TemporalType temporalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  @SuppressWarnings("deprecation")
  public TypedQuery<T> setParameter(int position, Calendar value, TemporalType temporalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  @SuppressWarnings("deprecation")
  public TypedQuery<T> setParameter(int position, Date value, TemporalType temporalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <U> TypedQuery<T> setParameter(Parameter<U> param, U value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public TypedQuery<T> setParameter(String name, Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public TypedQuery<T> setParameter(int position, Object value) {
    throw new UnsupportedOperationException();
  }
}
