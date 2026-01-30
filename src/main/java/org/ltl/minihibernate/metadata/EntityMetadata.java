package org.ltl.minihibernate.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds metadata about a mapped entity class.
 * Stores information from @Entity, @Table, plus field and relationship
 * metadata.
 */
public class EntityMetadata {

  private final Class<?> entityClass;
  private final String tableName;
  private final FieldMetadata idField;
  private final List<FieldMetadata> columns;
  private final List<FieldMetadata> relationships;

  private EntityMetadata(Builder builder) {
    this.entityClass = builder.entityClass;
    this.tableName = builder.tableName;
    this.idField = builder.idField;
    this.columns = Collections.unmodifiableList(new ArrayList<>(builder.columns));
    this.relationships = Collections.unmodifiableList(new ArrayList<>(builder.relationships));
  }

  public Class<?> getEntityClass() {
    return entityClass;
  }

  public String getTableName() {
    return tableName;
  }

  public FieldMetadata getIdField() {
    return idField;
  }

  /** All columns including ID (excludes relationships). */
  public List<FieldMetadata> getAllColumns() {
    List<FieldMetadata> all = new ArrayList<>();
    all.add(idField);
    all.addAll(columns);
    return all;
  }

  /** Non-ID columns only (excludes relationships). */
  public List<FieldMetadata> getColumns() {
    return columns;
  }

  /** All relationship fields (@ManyToOne, @OneToMany, etc.). */
  public List<FieldMetadata> getRelationships() {
    return relationships;
  }

  /** Get @ManyToOne relationships only. */
  public List<FieldMetadata> getManyToOneRelationships() {
    return relationships.stream()
        .filter(FieldMetadata::isManyToOne)
        .toList();
  }

  /** Get @OneToMany relationships only. */
  public List<FieldMetadata> getOneToManyRelationships() {
    return relationships.stream()
        .filter(FieldMetadata::isOneToMany)
        .toList();
  }

  /**
   * Returns all fields that map to a database column (ID + Basic Cols +
   * ManyToOne).
   */
  public List<FieldMetadata> getPersistableFields() {
    List<FieldMetadata> all = new ArrayList<>();
    all.add(idField);
    all.addAll(columns);
    all.addAll(getManyToOneRelationships());
    return all;
  }

  /**
   * Returns fields for UPDATE statements (Basic Cols + ManyToOne).
   */
  public List<FieldMetadata> getUpdatableFields() {
    List<FieldMetadata> all = new ArrayList<>();
    all.addAll(columns);
    all.addAll(getManyToOneRelationships());
    return all;
  }

  public Object newInstance() {
    try {
      return entityClass.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Cannot create instance of: " + entityClass.getName(), e);
    }
  }

  public Object getId(Object entity) {
    return idField.getValue(entity);
  }

  public void setId(Object entity, Object id) {
    idField.setValue(entity, id);
  }

  @Override
  public String toString() {
    return "EntityMetadata{entityClass=" + entityClass.getSimpleName() +
        ", tableName='" + tableName + "', columns=" + columns.size() +
        ", relationships=" + relationships.size() + "}";
  }

  public static Builder builder(Class<?> entityClass) {
    return new Builder(entityClass);
  }

  public static class Builder {
    private final Class<?> entityClass;
    private String tableName;
    private FieldMetadata idField;
    private List<FieldMetadata> columns = new ArrayList<>();
    private List<FieldMetadata> relationships = new ArrayList<>();

    public Builder(Class<?> entityClass) {
      this.entityClass = entityClass;
      this.tableName = entityClass.getSimpleName().toLowerCase();
    }

    public Builder tableName(String tableName) {
      this.tableName = tableName;
      return this;
    }

    public Builder idField(FieldMetadata idField) {
      this.idField = idField;
      return this;
    }

    public Builder addColumn(FieldMetadata column) {
      this.columns.add(column);
      return this;
    }

    public Builder addRelationship(FieldMetadata relationship) {
      this.relationships.add(relationship);
      return this;
    }

    public EntityMetadata build() {
      if (idField == null) {
        throw new IllegalStateException("Entity must have an @Id field");
      }
      return new EntityMetadata(this);
    }
  }
}
