package org.ltl.minihibernate.persist;

import jakarta.persistence.FetchType;
import java.sql.*;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.metadata.FieldMetadata;
import org.ltl.minihibernate.sql.SQLGenerator;

/**
 * Executes CRUD operations for entities. Works with SQLGenerator to create SQL and JDBC to execute.
 * Supports lazy loading for relationships.
 */
public class EntityPersister {

  private final Connection connection;
  private final SQLGenerator sqlGenerator;
  private final Function<Class<?>, EntityMetadata> metadataLookup;
  private final Function<Class<?>, FieldMetadata> foreignKeyLookup;
  private BiFunction<Class<?>, Object, Object> lazyLoader;

  public EntityPersister(
      Connection connection,
      SQLGenerator sqlGenerator,
      Function<Class<?>, EntityMetadata> metadataLookup) {
    this(connection, sqlGenerator, metadataLookup, null, null);
  }

  public EntityPersister(
      Connection connection,
      SQLGenerator sqlGenerator,
      Function<Class<?>, EntityMetadata> metadataLookup,
      BiFunction<Class<?>, Object, Object> lazyLoader,
      Function<Class<?>, FieldMetadata> foreignKeyLookup) {
    this.connection = connection;
    this.sqlGenerator = sqlGenerator;
    this.metadataLookup = metadataLookup;
    this.lazyLoader = lazyLoader;
    this.foreignKeyLookup = foreignKeyLookup;
  }

  /**
   * Inserts a new entity into the database.
   *
   * @return Generated ID if auto-generated, null otherwise
   */
  public Object insert(EntityMetadata metadata, Object entity) throws SQLException {
    String sql = sqlGenerator.generateInsert(metadata);
    List<FieldMetadata> columns = metadata.getPersistableFields();

    // Check if ID is auto-generated
    boolean hasGeneratedId = metadata.getIdField().isGeneratedValue();

    try (PreparedStatement ps =
        connection.prepareStatement(
            sql, hasGeneratedId ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {

      // Set parameters (skip ID if auto-generated)
      int paramIndex = 1;
      for (FieldMetadata field : columns) {
        if (field.isId() && hasGeneratedId) {
          continue; // Skip auto-generated ID
        }

        Object value = extractValue(entity, field);
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

  /** Loads an entity from the database by ID. relationResolver IS THE ENTITY MANAGER::FIND */
  public Object load(
      EntityMetadata metadata, Object id, BiFunction<Class<?>, Object, Object> relationResolver)
      throws SQLException {
    String sql = sqlGenerator.generateSelectById(metadata);

    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setObject(1, id);

      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return mapResultSet(rs, metadata, relationResolver, false);
        }
      }
    }

    return null;
  }

  /** Loads an entity from the database by ID with lazy loading support. */
  public Object loadLazy(
      EntityMetadata metadata,
      Object id,
      BiFunction<Class<?>, Object, Object> relationResolver,
      Function<Class<?>, FieldMetadata> fkLookup)
      throws SQLException {
    String sql = sqlGenerator.generateSelectById(metadata);

    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setObject(1, id);

      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return mapResultSet(rs, metadata, relationResolver, true, fkLookup);
        }
      }
    }

    return null;
  }

  /** Updates an existing entity in the database. */
  public void update(EntityMetadata metadata, Object entity) throws SQLException {
    String sql = sqlGenerator.generateUpdate(metadata);
    List<FieldMetadata> updatableColumns = metadata.getUpdatableFields();

    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      // Set column values
      int paramIndex = 1;
      for (FieldMetadata field : updatableColumns) {
        Object value = extractValue(entity, field);
        ps.setObject(paramIndex++, value);
      }

      // Set ID in WHERE clause
      Object id = metadata.getId(entity);
      ps.setObject(paramIndex, id);

      ps.executeUpdate();
    }
  }

  /** Deletes an entity from the database. */
  public void delete(EntityMetadata metadata, Object entity) throws SQLException {
    String sql = sqlGenerator.generateDelete(metadata);
    Object id = metadata.getId(entity);

    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setObject(1, id);
      ps.executeUpdate();
    }
  }

  /** Maps a ResultSet row to an entity instance (eager loading). */
  private Object mapResultSet(
      ResultSet rs, EntityMetadata metadata, BiFunction<Class<?>, Object, Object> relationResolver)
      throws SQLException {
    return mapResultSet(rs, metadata, relationResolver, false, null);
  }

  /** Maps a ResultSet row to an entity instance with optional lazy loading. */
  private Object mapResultSet(
      ResultSet rs,
      EntityMetadata metadata,
      BiFunction<Class<?>, Object, Object> relationResolver,
      boolean useLazyLoading)
      throws SQLException {
    return mapResultSet(rs, metadata, relationResolver, useLazyLoading, null);
  }

  /** Maps a ResultSet row to an entity instance with optional lazy loading. */
  private Object mapResultSet(
      ResultSet rs,
      EntityMetadata metadata,
      BiFunction<Class<?>, Object, Object> relationResolver,
      boolean useLazyLoading,
      Function<Class<?>, FieldMetadata> fkLookup)
      throws SQLException {
    Object entity = metadata.newInstance();

    for (FieldMetadata field : metadata.getPersistableFields()) {
      String columnName = field.getColumnName();
      Object value = rs.getObject(columnName);

      if (field.isManyToOne() && value != null) {
        if (useLazyLoading && field.getFetchType() == FetchType.LAZY && lazyLoader != null) {
          // Create a lazy proxy instead of loading immediately
          // Store the foreign key value and let the proxy load on access
          // For now, we store the FK value and the caller creates the proxy
          field.setValue(entity, value); // Store FK temporarily
        } else {
          // Eager loading - resolve relationship immediately
          if (relationResolver != null) {
            value = relationResolver.apply(field.getTargetEntity(), value);
          }
          field.setValue(entity, value);
        }
      } else if (field.isOneToMany() || field.isManyToMany()) {
        if (useLazyLoading && field.getFetchType() == FetchType.LAZY) {
          // Don't load the collection, let the proxy handle it
          // Set to null initially
          field.setValue(entity, null);
        } else {
          // Eager loading would load all related entities
          // For now, leave as null for collection fields
          field.setValue(entity, null);
        }
      } else {
        // Handle type conversion if needed
        value = convertType(value, field.getJavaType());
        field.setValue(entity, value);
      }
    }

    return entity;
  }

  private Object extractValue(Object entity, FieldMetadata field) {
    Object value = field.getValue(entity);
    if (field.isManyToOne() && value != null) {
      // It's an entity, we need its ID
      try {
        // Assuming the object is an entity, getting its metadata
        // We use the metadataLookup function
        Object relatedId = metadataLookup.apply(value.getClass()).getId(value);
        return relatedId;
      } catch (Exception e) {
        throw new RuntimeException("Could not extract ID from related entity " + value, e);
      }
    }
    return value;
  }

  /** Converts JDBC types to Java types as needed. */
  private Object convertType(Object value, Class<?> targetType) {
    if (Objects.isNull(value) || targetType == null) {
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

  /** Get the lazy loader function. */
  public BiFunction<Class<?>, Object, Object> getLazyLoader() {
    return lazyLoader;
  }

  /** Set the lazy loader function. */
  public void setLazyLoader(BiFunction<Class<?>, Object, Object> lazyLoader) {
    this.lazyLoader = lazyLoader;
  }
}
