package org.ltl.minihibernate.internal;

import org.ltl.minihibernate.api.MiniTypedQuery;
import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.metadata.FieldMetadata;
import org.ltl.minihibernate.query.SimpleJPQLParser;
import org.ltl.minihibernate.session.EntityState;
import org.ltl.minihibernate.sql.SQLGenerator;
import io.vavr.collection.List;
import io.vavr.control.Try;
import jakarta.persistence.CacheRetrieveMode;
import jakarta.persistence.CacheStoreMode;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Parameter;
import jakarta.persistence.TemporalType;
import jakarta.persistence.TypedQuery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * MiniTypedQueryImpl - Query implementation.
 */
public class MiniTypedQueryImpl<T> implements MiniTypedQuery<T>, TypedQuery<T> {

  private final Class<T> entityClass;
  private final MiniEntityManagerImpl entityManager;
  private final EntityMetadata metadata;
  private final SQLGenerator sqlGenerator;

  // Builder state
  private String whereClause = null;
  private java.util.List<Object> positionalParams = new ArrayList<>();

  // JPQL state
  private String jpql = null;
  private Map<String, Object> namedParams = new HashMap<>();

  // Standard state
  private Integer maxResults = null;
  private Integer firstResult = null;

  MiniTypedQueryImpl(Class<T> entityClass, MiniEntityManagerImpl entityManager) {
    this.entityClass = entityClass;
    this.entityManager = entityManager;
    this.metadata = entityManager.getMetadata(entityClass);
    this.sqlGenerator = new SQLGenerator();
  }

  // Constructor for JPQL
  MiniTypedQueryImpl(String jpql, Class<T> entityClass, MiniEntityManagerImpl entityManager) {
    this(entityClass, entityManager);
    this.jpql = jpql;
  }

  @Override
  public MiniTypedQuery<T> where(String field, Object value) {
    if (jpql != null)
      throw new IllegalStateException("Cannot use where() with JPQL");
    String columnName = findColumnName(field);

    if (whereClause == null) {
      whereClause = columnName + " = ?";
    } else {
      whereClause += " AND " + columnName + " = ?";
    }
    positionalParams.add(value);
    return this;
  }

  @Override
  public MiniTypedQueryImpl<T> setMaxResults(int maxResult) {
    this.maxResults = maxResult;
    return this;
  }

  @Override
  public MiniTypedQueryImpl<T> setFirstResult(int startPosition) {
    this.firstResult = startPosition;
    return this;
  }

  @Override
  public java.util.List<T> getResultList() {
    String sql = buildSQL();

    return Try.of(() -> {
      java.util.List<T> results = new ArrayList<>();
      Connection conn = entityManager.getConnection();

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        // Set parameters
        java.util.List<Object> finalParams = getFinalParameters();
        for (int i = 0; i < finalParams.size(); i++) {
          ps.setObject(i + 1, finalParams.get(i));
        }

        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            T entity = mapResultSet(rs);
            entityManager.getPersistenceContext().addEntity(
                entity, metadata, EntityState.MANAGED);
            results.add(entity);
          }
        }
      }

      return results;
    }).getOrElseThrow(e -> new RuntimeException("Query failed", e));
  }

  private String buildSQL() {
    if (jpql != null) {
      SimpleJPQLParser.ParsedQuery pq = SimpleJPQLParser.parse(jpql, metadata);
      String sql = pq.sql;
      if (maxResults != null)
        sql += " LIMIT " + maxResults;
      if (firstResult != null)
        sql += " OFFSET " + firstResult;
      return sql;
    } else {
      String sql;
      if (whereClause != null) {
        sql = sqlGenerator.generateSelectWhere(metadata, whereClause);
      } else {
        sql = sqlGenerator.generateSelectAll(metadata);
      }
      if (maxResults != null)
        sql += " LIMIT " + maxResults;
      if (firstResult != null)
        sql += " OFFSET " + firstResult;
      return sql;
    }
  }

  private java.util.List<Object> getFinalParameters() {
    if (jpql != null) {
      SimpleJPQLParser.ParsedQuery pq = SimpleJPQLParser.parse(jpql, metadata);
      java.util.List<Object> params = new ArrayList<>();
      for (String name : pq.paramNames) {
        if (!namedParams.containsKey(name)) {
          throw new IllegalArgumentException("Missing parameter: " + name);
        }
        params.add(namedParams.get(name));
      }
      return params;
    } else {
      return positionalParams;
    }
  }

  @Override
  public T getSingleResult() {
    java.util.List<T> results = getResultList();
    if (results.isEmpty()) {
      throw new RuntimeException("No result found");
    }
    if (results.size() > 1) {
      throw new RuntimeException("Expected single result, got " + results.size());
    }
    return results.get(0);
  }

  // ==================== TypedQuery Implementation ====================

  @Override
  public TypedQuery<T> setParameter(String name, Object value) {
    // Only for JPQL
    namedParams.put(name, value);
    return this;
  }

  // Stubs for other TypedQuery methods
  @Override
  public TypedQuery<T> setHint(String hintName, Object value) {
    return this;
  }

  @Override
  public Map<String, Object> getHints() {
    return new HashMap<>();
  }

  @Override
  public TypedQuery<T> setFlushMode(FlushModeType flushMode) {
    return this;
  }

  @Override
  public FlushModeType getFlushMode() {
    return FlushModeType.AUTO;
  }

  @Override
  public TypedQuery<T> setLockMode(LockModeType lockMode) {
    return this;
  }

  @Override
  public LockModeType getLockMode() {
    return LockModeType.NONE;
  }

  @Override
  public <T1> T1 unwrap(Class<T1> cls) {
    return null;
  }

  @Override
  public Parameter<?> getParameter(String name) {
    return null;
  }

  @Override
  public Parameter<?> getParameter(int position) {
    return null;
  }

  @Override
  public <T1> Parameter<T1> getParameter(String name, Class<T1> type) {
    return null;
  }

  @Override
  public <T1> Parameter<T1> getParameter(int position, Class<T1> type) {
    return null;
  }

  @Override
  public boolean isBound(Parameter<?> param) {
    return true;
  }

  @Override
  public <T1> T1 getParameterValue(Parameter<T1> param) {
    return null;
  }

  @Override
  public Object getParameterValue(String name) {
    return namedParams.get(name);
  }

  @Override
  public Object getParameterValue(int position) {
    return null;
  }

  @Override
  public Set<Parameter<?>> getParameters() {
    return null;
  }

  @Override
  public TypedQuery<T> setParameter(Parameter<Calendar> param, Calendar value, TemporalType temporalType) {
    return this;
  }

  @Override
  public TypedQuery<T> setParameter(Parameter<Date> param, Date value, TemporalType temporalType) {
    return this;
  }

  @Override
  public TypedQuery<T> setParameter(String name, Calendar value, TemporalType temporalType) {
    return this;
  }

  @Override
  public TypedQuery<T> setParameter(String name, Date value, TemporalType temporalType) {
    return this;
  }

  @Override
  public TypedQuery<T> setParameter(int position, Calendar value, TemporalType temporalType) {
    return this;
  }

  @Override
  public TypedQuery<T> setParameter(int position, Date value, TemporalType temporalType) {
    return this;
  }

  // Generic <T> definition from interface
  @Override
  public <U> TypedQuery<T> setParameter(Parameter<U> param, U value) {
    return this;
  }

  @Override
  public TypedQuery<T> setParameter(int position, Object value) {
    return this;
  }

  @Override
  public int executeUpdate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Stream<T> getResultStream() {
    return getResultList().stream();
  }

  // ==================== Private Helpers ====================

  private String findColumnName(String fieldName) {
    return List.ofAll(metadata.getPersistableFields()) // use valid fields
        .find(f -> f.getFieldName().equals(fieldName))
        .map(FieldMetadata::getColumnName)
        .getOrElse(fieldName);
  }

  @SuppressWarnings("unchecked")
  private T mapResultSet(ResultSet rs) throws Exception {
    Object entity = metadata.newInstance();

    for (FieldMetadata field : metadata.getPersistableFields()) {
      String columnName = field.getColumnName();
      Object value = rs.getObject(columnName);

      if (field.isManyToOne() && value != null) {
        // Resolve relationship
        value = entityManager.find(field.getTargetEntity(), value);
      } else {
        value = convertType(value, field.getJavaType());
      }

      field.setValue(entity, value);
    }

    return (T) entity;
  }

  private Object convertType(Object value, Class<?> targetType) {
    if (value == null)
      return null;
    if (targetType == Long.class || targetType == long.class) {
      if (value instanceof Number)
        return ((Number) value).longValue();
    } else if (targetType == Integer.class || targetType == int.class) {
      if (value instanceof Number)
        return ((Number) value).intValue();
    }
    return value;
  }

  @Override
  public int getMaxResults() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'getMaxResults'");
  }

  @Override
  public int getFirstResult() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'getFirstResult'");
  }

  @Override
  public CacheRetrieveMode getCacheRetrieveMode() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'getCacheRetrieveMode'");
  }

  @Override
  public CacheStoreMode getCacheStoreMode() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'getCacheStoreMode'");
  }

  @Override
  public Integer getTimeout() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'getTimeout'");
  }

  @Override
  public T getSingleResultOrNull() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'getSingleResultOrNull'");
  }

  @Override
  public TypedQuery<T> setCacheRetrieveMode(CacheRetrieveMode cacheRetrieveMode) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'setCacheRetrieveMode'");
  }

  @Override
  public TypedQuery<T> setCacheStoreMode(CacheStoreMode cacheStoreMode) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'setCacheStoreMode'");
  }

  @Override
  public TypedQuery<T> setTimeout(Integer timeout) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'setTimeout'");
  }
}
