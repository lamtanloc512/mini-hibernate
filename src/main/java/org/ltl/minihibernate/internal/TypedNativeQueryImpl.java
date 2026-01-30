package org.ltl.minihibernate.internal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.metadata.MetadataParser;
import org.ltl.minihibernate.session.EntityState;

import jakarta.persistence.CacheRetrieveMode;
import jakarta.persistence.CacheStoreMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Parameter;
import jakarta.persistence.TemporalType;
import jakarta.persistence.TypedQuery;

/**
 * Extended native query implementation with entity and DTO mapping support.
 *
 * @param <T> The result type
 */
public class TypedNativeQueryImpl<T> extends MiniNativeQueryImpl implements TypedQuery<T> {

  private final Class<T> resultClass;
  private final MiniEntityManagerImpl entityManager;
  private final MetadataParser metadataParser;
  private final NativeQueryMapper mapper;

  // For entity mapping
  private Class<? extends T> entityClass;

  // For DTO mapping
  private String constructorExpression;

  // For scalar results
  private boolean isScalar;

  public TypedNativeQueryImpl(
      String sql,
      Connection connection,
      Class<T> resultClass,
      MiniEntityManagerImpl entityManager) {
    super(sql, connection);
    this.resultClass = resultClass;
    this.entityManager = entityManager;
    this.metadataParser = new MetadataParser();
    this.mapper = new NativeQueryMapper(metadataParser);
    this.isScalar = false;
    
    // Automatically set entityClass if resultClass is an entity
    if (entityManager != null && entityManager.getFactory() != null && 
        entityManager.getFactory().getEntityClass(resultClass.getSimpleName()) != null) {
      this.entityClass = resultClass;
    }
  }

  /** Set entity class for mapping. */
  public TypedNativeQueryImpl<T> setEntityClass(Class<? extends T> entityClass) {
    this.entityClass = entityClass;
    return this;
  }

  /**
   * Set constructor expression for DTO mapping. Example: "new com.example.UserDTO(u.id, u.name)"
   */
  public TypedNativeQueryImpl<T> setConstructorExpression(String constructorExpression) {
    this.constructorExpression = constructorExpression;
    return this;
  }

  /** Mark as scalar query (returns Object[]). */
  public TypedNativeQueryImpl<T> setScalar(boolean scalar) {
    this.isScalar = scalar;
    return this;
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public List getResultList() {
    // If we have entityClass or it's an entity, use getEntityResultList
    if (entityClass != null || (resultClass != null && resultClass.isAnnotationPresent(Entity.class))) {
      return getEntityResultList();
    }
    return getTypedResultList();
  }

  /** Get typed results as a list of T. */
  public List<T> getTypedResultList() {
    String jdbcSql = getJdbcSql();
    List<T> results = new ArrayList<>();

    try (PreparedStatement stmt = getConnection().prepareStatement(jdbcSql)) {
      for (int i = 0; i < parameters.size(); i++) {
        stmt.setObject(i + 1, parameters.get(i));
      }

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          T result = mapResultSet(rs);
          results.add(result);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Native query failed", e);
    }

    return results;
  }

  @Override
  public T getSingleResult() {
    @SuppressWarnings("unchecked")
    List<T> results = getResultList();
    if (results.isEmpty())
      return null;
    return results.get(0);
  }

  @SuppressWarnings("unchecked")
  private T mapResultSet(ResultSet rs) throws Exception {
    // Check for constructor expression (DTO mapping)
    if (constructorExpression != null && !constructorExpression.isEmpty()) {
      return (T) mapper.mapToDTO(rs, constructorExpression, resultClass);
    }

    // Check for entity mapping
    if ((entityClass != null || (resultClass != null && resultClass.isAnnotationPresent(Entity.class))) 
        && !resultClass.isArray() && !resultClass.equals(Object.class)) {
      Class<?> targetEntityClass = entityClass != null ? entityClass : resultClass;
      return (T) mapper.mapToEntity(rs, targetEntityClass);
    }

    // Scalar result (Object[])
    if (isScalar || resultClass.equals(Object[].class)) {
      ResultSetMetaData rsMetaData = rs.getMetaData();
      int columnCount = rsMetaData.getColumnCount();
      Object[] row = new Object[columnCount];
      for (int i = 1; i <= columnCount; i++) {
        row[i - 1] = rs.getObject(i);
      }
      return (T) row;
    }

    // Single column result
    ResultSetMetaData rsMetaData = rs.getMetaData();
    int columnCount = rsMetaData.getColumnCount();

    if (columnCount == 1) {
      return (T) rs.getObject(1);
    }

    // Default: return Object[]
    Object[] row = new Object[columnCount];
    for (int i = 1; i <= columnCount; i++) {
      row[i - 1] = rs.getObject(i);
    }
    return (T) row;
  }

  /** Get results as a list of entities, registered in the persistence context. */
  @SuppressWarnings("unchecked")
  public List<T> getEntityResultList() {
    String jdbcSql = getJdbcSql();
    List<T> results = new ArrayList<>();

    try (PreparedStatement stmt = getConnection().prepareStatement(jdbcSql)) {
      for (int i = 0; i < parameters.size(); i++) {
        stmt.setObject(i + 1, parameters.get(i));
      }

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          T entity =
              mapper.mapToEntity(rs, entityClass != null ? (Class<T>) entityClass : resultClass);
          if (entityManager != null) {
            EntityMetadata metadata = metadataParser.parse(entity.getClass());
            entityManager.getPersistenceContext().addEntity(entity, metadata, EntityState.MANAGED);
          }
          results.add(entity);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Native query failed", e);
    }

    return results;
  }

  // --- TypedQuery implementation overloads ---

  @Override
  public TypedQuery<T> setMaxResults(int maxResults) {
    super.setMaxResults(maxResults);
    return this;
  }

  @Override
  public TypedQuery<T> setFirstResult(int startPosition) {
    super.setFirstResult(startPosition);
    return this;
  }

  @Override
  public TypedQuery<T> setHint(String hintName, Object value) {
    super.setHint(hintName, value);
    return this;
  }

  @Override
  public <U> TypedQuery<T> setParameter(Parameter<U> param, U value) {
    super.setParameter(param, value);
    return this;
  }

  @Override
  public TypedQuery<T> setParameter(Parameter<Calendar> param, Calendar value, TemporalType temporalType) {
    super.setParameter(param, value, temporalType);
    return this;
  }

  @Override
  public TypedQuery<T> setParameter(Parameter<Date> param, Date value, TemporalType temporalType) {
    super.setParameter(param, value, temporalType);
    return this;
  }

  @Override
  public TypedQuery<T> setParameter(String name, Object value) {
    super.setParameter(name, value);
    return this;
  }

  @Override
  public TypedQuery<T> setParameter(String name, Calendar value, TemporalType temporalType) {
    super.setParameter(name, value, temporalType);
    return this;
  }

  @Override
  public TypedQuery<T> setParameter(String name, Date value, TemporalType temporalType) {
    super.setParameter(name, value, temporalType);
    return this;
  }

  @Override
  public TypedQuery<T> setParameter(int position, Object value) {
    super.setParameter(position, value);
    return this;
  }

  @Override
  public TypedQuery<T> setParameter(int position, Calendar value, TemporalType temporalType) {
    super.setParameter(position, value, temporalType);
    return this;
  }

  @Override
  public TypedQuery<T> setParameter(int position, Date value, TemporalType temporalType) {
    super.setParameter(position, value, temporalType);
    return this;
  }

  @Override
  public TypedQuery<T> setFlushMode(FlushModeType flushMode) {
    super.setFlushMode(flushMode);
    return this;
  }

  @Override
  public TypedQuery<T> setLockMode(LockModeType lockMode) {
    return this;
  }

  @Override
  public T getSingleResultOrNull() {
    @SuppressWarnings("unchecked")
    List<T> results = getResultList();
    return results.isEmpty() ? null : results.get(0);
  }

  @Override
  public TypedQuery<T> setCacheRetrieveMode(CacheRetrieveMode cacheRetrieveMode) {
    return this;
  }

  @Override
  public TypedQuery<T> setCacheStoreMode(CacheStoreMode cacheStoreMode) {
    return this;
  }

  @Override
  public TypedQuery<T> setTimeout(Integer timeout) {
    return this;
  }
}
