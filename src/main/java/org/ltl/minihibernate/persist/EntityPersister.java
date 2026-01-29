package org.ltl.minihibernate.persist;

import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.metadata.FieldMetadata;
import org.ltl.minihibernate.sql.SQLGenerator;

import java.sql.*;
import java.util.List;

/**
 * Executes CRUD operations for entities.
 * Works with SQLGenerator to create SQL and JDBC to execute.
 */
public class EntityPersister {

  private final Connection connection;
  private final SQLGenerator sqlGenerator;

  public EntityPersister(Connection connection, SQLGenerator sqlGenerator) {
    this.connection = connection;
    this.sqlGenerator = sqlGenerator;
  }

  /**
   * Inserts a new entity into the database.
   * 
   * @return Generated ID if auto-generated, null otherwise
   */
  public Object insert(EntityMetadata metadata, Object entity) throws SQLException {
    String sql = sqlGenerator.generateInsert(metadata);
    List<FieldMetadata> columns = metadata.getAllColumns();

    // Check if ID is auto-generated
    boolean hasGeneratedId = metadata.getIdField().isGeneratedValue();

    try (PreparedStatement ps = connection.prepareStatement(sql,
        hasGeneratedId ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {

      // Set parameters (skip ID if auto-generated)
      int paramIndex = 1;
      for (FieldMetadata field : columns) {
        if (field.isId() && hasGeneratedId) {
          continue; // Skip auto-generated ID
        }
        Object value = field.getValue(entity);
        ps.setObject(paramIndex++, value);
      }

      ps.executeUpdate();

      // Get generated ID and convert to correct type
      if (hasGeneratedId) {
        try (ResultSet rs = ps.getGeneratedKeys()) {
          if (rs.next()) {
            Object generatedId = rs.getObject(1);
            // Convert to correct ID type (e.g., BigInteger → Long)
            return convertType(generatedId, metadata.getIdField().getJavaType());
          }
        }
      }

      return null;
    }
  }

  /**
   * Loads an entity from the database by ID.
   */
  public Object load(EntityMetadata metadata, Object id) throws SQLException {
    String sql = sqlGenerator.generateSelectById(metadata);

    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setObject(1, id);

      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return mapResultSet(rs, metadata);
        }
      }
    }

    return null;
  }

  /**
   * Updates an existing entity in the database.
   */
  public void update(EntityMetadata metadata, Object entity) throws SQLException {
    String sql = sqlGenerator.generateUpdate(metadata);
    List<FieldMetadata> nonIdColumns = metadata.getColumns();

    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      // Set column values
      int paramIndex = 1;
      for (FieldMetadata field : nonIdColumns) {
        Object value = field.getValue(entity);
        ps.setObject(paramIndex++, value);
      }

      // Set ID in WHERE clause
      Object id = metadata.getId(entity);
      ps.setObject(paramIndex, id);

      ps.executeUpdate();
    }
  }

  /**
   * Deletes an entity from the database.
   */
  public void delete(EntityMetadata metadata, Object entity) throws SQLException {
    String sql = sqlGenerator.generateDelete(metadata);
    Object id = metadata.getId(entity);

    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setObject(1, id);
      ps.executeUpdate();
    }
  }

  /**
   * Maps a ResultSet row to an entity instance.
   */
  private Object mapResultSet(ResultSet rs, EntityMetadata metadata) throws SQLException {
    Object entity = metadata.newInstance();

    for (FieldMetadata field : metadata.getAllColumns()) {
      String columnName = field.getColumnName();
      Object value = rs.getObject(columnName);

      // Handle type conversion if needed
      value = convertType(value, field.getJavaType());
      field.setValue(entity, value);
    }

    return entity;
  }

  /**
   * Converts JDBC types to Java types as needed.
   */
  private Object convertType(Object value, Class<?> targetType) {
    if (value == null) {
      return null;
    }

    // Handle common type mismatches
    if (targetType == Long.class || targetType == long.class) {
      if (value instanceof Number) {
        return ((Number) value).longValue();
      }
    } else if (targetType == Integer.class || targetType == int.class) {
      if (value instanceof Number) {
        return ((Number) value).intValue();
      }
    } else if (targetType == String.class) {
      return value.toString();
    }

    return value;
  }
}
