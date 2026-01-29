package org.ltl.minihibernate.query;

import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.metadata.FieldMetadata;
import org.ltl.minihibernate.session.MiniSession;
import org.ltl.minihibernate.sql.SQLGenerator;
import io.vavr.collection.List;
import io.vavr.control.Try;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

/**
 * Simple query builder for entities.
 * 
 * Provides basic querying capabilities:
 * - where(field, value)
 * - findAll()
 * - getSingleResult() / getResultList()
 */
public class MiniQuery<T> {

  private final MiniSession session;
  private final EntityMetadata metadata;
  private final SQLGenerator sqlGenerator;

  private String whereClause = null;
  private java.util.List<Object> parameters = new ArrayList<>();
  private Integer maxResults = null;
  private Integer firstResult = null;

  public MiniQuery(Class<T> entityClass, MiniSession session) {
    this.session = session;
    this.metadata = session.getMetadata(entityClass);
    this.sqlGenerator = new SQLGenerator();
  }

  /**
   * Adds a simple equality condition.
   * 
   * Example: query.where("name", "John")
   */
  public MiniQuery<T> where(String field, Object value) {
    // Find the column name for the field
    String columnName = findColumnName(field);

    if (whereClause == null) {
      whereClause = columnName + " = ?";
    } else {
      whereClause += " AND " + columnName + " = ?";
    }
    parameters.add(value);

    return this;
  }

  /**
   * Adds a custom WHERE clause.
   * 
   * Example: query.whereClause("status = ? AND created_at > ?", 1, someDate)
   */
  public MiniQuery<T> whereClause(String clause, Object... params) {
    this.whereClause = clause;
    this.parameters.clear();
    for (Object param : params) {
      this.parameters.add(param);
    }
    return this;
  }

  /**
   * Sets maximum number of results to return.
   */
  public MiniQuery<T> setMaxResults(int max) {
    this.maxResults = max;
    return this;
  }

  /**
   * Sets the first result offset (for pagination).
   */
  public MiniQuery<T> setFirstResult(int offset) {
    this.firstResult = offset;
    return this;
  }

  /**
   * Executes query and returns list of results.
   */
  public java.util.List<T> getResultList() {
    String sql = buildSQL();

    return Try.of(() -> {
      java.util.List<T> results = new ArrayList<>();
      Connection conn = session.getConnection();

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        // Set parameters
        for (int i = 0; i < parameters.size(); i++) {
          ps.setObject(i + 1, parameters.get(i));
        }

        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            T entity = mapResultSet(rs);
            // Add to persistence context
            session.getPersistenceContext().addEntity(
                entity,
                metadata,
                org.ltl.minihibernate.session.EntityState.MANAGED);
            results.add(entity);
          }
        }
      }

      return results;
    }).getOrElseThrow(e -> new RuntimeException("Query failed", e));
  }

  /**
   * Executes query and returns single result.
   * 
   * @throws RuntimeException if no result or more than one
   */
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

  /**
   * Executes query and returns first result or null.
   */
  public T getFirstResult() {
    setMaxResults(1);
    java.util.List<T> results = getResultList();
    return results.isEmpty() ? null : results.get(0);
  }

  /**
   * Executes query and returns result count.
   */
  public long count() {
    String sql = "SELECT COUNT(*) FROM " + metadata.getTableName();
    if (whereClause != null) {
      sql += " WHERE " + whereClause;
    }

    final String finalSql = sql;
    return Try.of(() -> {
      Connection conn = session.getConnection();
      try (PreparedStatement ps = conn.prepareStatement(finalSql)) {
        for (int i = 0; i < parameters.size(); i++) {
          ps.setObject(i + 1, parameters.get(i));
        }
        try (ResultSet rs = ps.executeQuery()) {
          return rs.next() ? rs.getLong(1) : 0L;
        }
      }
    }).getOrElseThrow(e -> new RuntimeException("Count failed", e));
  }

  // ==================== Internal ====================

  private String buildSQL() {
    String sql;
    if (whereClause != null) {
      sql = sqlGenerator.generateSelectWhere(metadata, whereClause);
    } else {
      sql = sqlGenerator.generateSelectAll(metadata);
    }

    // Add LIMIT/OFFSET if specified
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
        .getOrElse(fieldName); // Assume field name = column name if not found
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
    } else if (targetType == String.class) {
      return value.toString();
    }

    return value;
  }
}
