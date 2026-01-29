package org.ltl.minihibernate.internal;

import org.ltl.minihibernate.api.MiniTypedQuery;
import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.metadata.FieldMetadata;
import org.ltl.minihibernate.session.EntityState;
import org.ltl.minihibernate.sql.SQLGenerator;
import io.vavr.collection.List;
import io.vavr.control.Try;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

/**
 * MiniTypedQueryImpl - Query implementation.
 * 
 * Like Hibernate's QueryImpl.
 */
public class MiniTypedQueryImpl<T> implements MiniTypedQuery<T> {

  @SuppressWarnings("unused")
  private final Class<T> entityClass;
  private final MiniEntityManagerImpl entityManager;
  private final EntityMetadata metadata;
  private final SQLGenerator sqlGenerator;

  private String whereClause = null;
  private java.util.List<Object> parameters = new ArrayList<>();
  private Integer maxResults = null;
  private Integer firstResult = null;

  MiniTypedQueryImpl(Class<T> entityClass, MiniEntityManagerImpl entityManager) {
    this.entityClass = entityClass;
    this.entityManager = entityManager;
    this.metadata = entityManager.getMetadata(entityClass);
    this.sqlGenerator = new SQLGenerator();
  }

  @Override
  public MiniTypedQuery<T> where(String field, Object value) {
    String columnName = findColumnName(field);

    if (whereClause == null) {
      whereClause = columnName + " = ?";
    } else {
      whereClause += " AND " + columnName + " = ?";
    }
    parameters.add(value);
    return this;
  }

  @Override
  public MiniTypedQuery<T> setMaxResults(int maxResult) {
    this.maxResults = maxResult;
    return this;
  }

  @Override
  public MiniTypedQuery<T> setFirstResult(int startPosition) {
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
        for (int i = 0; i < parameters.size(); i++) {
          ps.setObject(i + 1, parameters.get(i));
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

  private String buildSQL() {
    String sql;
    if (whereClause != null) {
      sql = sqlGenerator.generateSelectWhere(metadata, whereClause);
    } else {
      sql = sqlGenerator.generateSelectAll(metadata);
    }

    if (maxResults != null) {
      sql += " LIMIT " + maxResults;
    }
    if (firstResult != null) {
      sql += " OFFSET " + firstResult;
    }

    return sql;
  }

  private String findColumnName(String fieldName) {
    return List.ofAll(metadata.getAllColumns())
        .find(f -> f.getFieldName().equals(fieldName))
        .map(FieldMetadata::getColumnName)
        .getOrElse(fieldName);
  }

  @SuppressWarnings("unchecked")
  private T mapResultSet(ResultSet rs) throws Exception {
    Object entity = metadata.newInstance();

    for (FieldMetadata field : metadata.getAllColumns()) {
      String columnName = field.getColumnName();
      Object value = rs.getObject(columnName);
      value = convertType(value, field.getJavaType());
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
}
