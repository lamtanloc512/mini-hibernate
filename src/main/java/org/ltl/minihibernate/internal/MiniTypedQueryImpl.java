package org.ltl.minihibernate.internal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.ltl.minihibernate.api.MiniTypedQuery;
import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.metadata.FieldMetadata;
import org.ltl.minihibernate.query.EnhancedJPQLParser;
import org.ltl.minihibernate.session.EntityState;
import org.ltl.minihibernate.sql.SQLGenerator;

import io.vavr.collection.List;
import io.vavr.control.Try;
import jakarta.persistence.Parameter;
import jakarta.persistence.TypedQuery;

/**
 * MiniTypedQueryImpl - Query implementation.
 */
public class MiniTypedQueryImpl<T> extends AbstractQuery<T> implements MiniTypedQuery<T> {

  @SuppressWarnings("unused")
  private final Class<T> entityClass;
  private final MiniEntityManagerImpl entityManager;
  private final EntityMetadata metadata;
  private final SQLGenerator sqlGenerator;

  // Builder state
  private String whereClause = null;
  private java.util.List<Object> positionalParams = new ArrayList<>();

  // JPQL state
  private String jpql = null;
  private final Map<String, Object> namedParams = new HashMap<>();

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
  public MiniTypedQueryImpl<T> setMaxResults(int maxResults) {
    super.setMaxResults(maxResults);
    return this;
  }

  @Override
  public MiniTypedQueryImpl<T> setFirstResult(int startPosition) {
    super.setFirstResult(startPosition);
    return this;
  }

  @Override
  public java.util.List<T> getResultList() {
    String sql = buildSQL();
    java.util.List<Object> finalParams = getFinalParameters();

    return Try.of(() -> {
      java.util.List<T> results = new ArrayList<>();
      Connection conn = entityManager.getConnection();

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        // Set parameters
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
      EnhancedJPQLParser.ParsedQuery pq = EnhancedJPQLParser.parse(jpql, metadata);
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
      EnhancedJPQLParser.ParsedQuery pq = EnhancedJPQLParser.parse(jpql, metadata);
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

  @Override
  public Parameter<?> getParameter(String name) {
    return new Parameter<Object>() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public Integer getPosition() {
        return null;
      }

      @Override
      public Class<Object> getParameterType() {
        return Object.class;
      }
    };
  }

  @Override
  public Parameter<?> getParameter(int position) {
    return new Parameter<Object>() {
      @Override
      public String getName() {
        return null;
      }

      @Override
      public Integer getPosition() {
        return position;
      }

      @Override
      public Class<Object> getParameterType() {
        return Object.class;
      }
    };
  }

  @Override
  public boolean isBound(Parameter<?> param) {
    if (param.getName() != null) {
      return namedParams.containsKey(param.getName());
    }
    if (param.getPosition() != null) {
      return param.getPosition() <= positionalParams.size() && positionalParams.get(param.getPosition() - 1) != null;
    }
    return false;
  }

  @Override
  public Object getParameterValue(String name) {
    return namedParams.get(name);
  }

  @Override
  public Set<Parameter<?>> getParameters() {
    Set<Parameter<?>> result = new java.util.HashSet<>();
    if (jpql != null) {
      EnhancedJPQLParser.ParsedQuery pq = EnhancedJPQLParser.parse(jpql, metadata);
      for (String name : pq.paramNames) {
        result.add(getParameter(name));
      }
    } else {
      for (int i = 0; i < positionalParams.size(); i++) {
        result.add(getParameter(i + 1));
      }
    }
    return result;
  }

  // Generic <T> definition from interface
  @Override
  public <U> TypedQuery<T> setParameter(Parameter<U> param, U value) {
    if (param.getName() != null) {
      return setParameter(param.getName(), value);
    }
    if (param.getPosition() != null) {
      return setParameter(param.getPosition(), value);
    }
    return this;
  }

  @Override
  public TypedQuery<T> setParameter(int position, Object value) {
    while (positionalParams.size() < position) {
      positionalParams.add(null);
    }
    positionalParams.set(position - 1, value);
    return this;
  }

  @Override
  public int executeUpdate() {
    throw new UnsupportedOperationException();
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
}
