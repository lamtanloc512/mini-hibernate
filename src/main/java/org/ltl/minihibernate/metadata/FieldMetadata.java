package org.ltl.minihibernate.metadata;

import jakarta.persistence.FetchType;
import java.lang.reflect.Field;

/**
 * Holds metadata about a mapped field/column.
 * Supports both regular columns and relationship mappings.
 */
public class FieldMetadata {

  private final Field field;
  private final String columnName;
  private final boolean isId;
  private final boolean isGeneratedValue;
  private final boolean nullable;
  private final int length;
  private final boolean unique;
  
  // Relationship fields
  private final RelationshipType relationshipType;
  private final Class<?> targetEntity;
  private final String mappedBy;
  private final FetchType fetchType;
  private final boolean isLazy;

  private FieldMetadata(Builder builder) {
    this.field = builder.field;
    this.columnName = builder.columnName;
    this.isId = builder.isId;
    this.isGeneratedValue = builder.isGeneratedValue;
    this.nullable = builder.nullable;
    this.length = builder.length;
    this.unique = builder.unique;
    this.relationshipType = builder.relationshipType;
    this.targetEntity = builder.targetEntity;
    this.mappedBy = builder.mappedBy;
    this.fetchType = builder.fetchType;
    this.isLazy = builder.isLazy;

    this.field.setAccessible(true);
  }

  public Field getField() { return field; }
  public String getFieldName() { return field.getName(); }
  public String getColumnName() { return columnName; }
  public Class<?> getJavaType() { return field.getType(); }
  public boolean isId() { return isId; }
  public boolean isGeneratedValue() { return isGeneratedValue; }
  public boolean isNullable() { return nullable; }
  public int getLength() { return length; }
  public boolean isUnique() { return unique; }
  
  // Relationship getters
  public RelationshipType getRelationshipType() { return relationshipType; }
  public Class<?> getTargetEntity() { return targetEntity; }
  public String getMappedBy() { return mappedBy; }
  public FetchType getFetchType() { return fetchType; }
  public boolean isLazy() { return isLazy; }
  public boolean isEager() { return fetchType == FetchType.EAGER; }
  public boolean isRelationship() { return relationshipType != RelationshipType.NONE; }
  public boolean isManyToOne() { return relationshipType == RelationshipType.MANY_TO_ONE; }
  public boolean isOneToMany() { return relationshipType == RelationshipType.ONE_TO_MANY; }
  public boolean isOneToOne() { return relationshipType == RelationshipType.ONE_TO_ONE; }
  public boolean isManyToMany() { return relationshipType == RelationshipType.MANY_TO_MANY; }

  public Object getValue(Object entity) {
    try {
      return field.get(entity);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Cannot access field: " + field.getName(), e);
    }
  }

  public void setValue(Object entity, Object value) {
    try {
      field.set(entity, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Cannot set field: " + field.getName(), e);
    }
  }

  @Override
  public String toString() {
    String rel = relationshipType != RelationshipType.NONE ? ", relationship=" + relationshipType : "";
    return "FieldMetadata{field=" + field.getName() + ", columnName='" + columnName + "'" +
        ", isId=" + isId + rel + "}";
  }

  public static Builder builder(Field field) {
    return new Builder(field);
  }

  public static class Builder {
    private final Field field;
    private String columnName;
    private boolean isId = false;
    private boolean isGeneratedValue = false;
    private boolean nullable = true;
    private int length = 255;
    private boolean unique = false;
    private RelationshipType relationshipType = RelationshipType.NONE;
    private Class<?> targetEntity;
    private String mappedBy = "";
    private FetchType fetchType = FetchType.EAGER;
    private boolean isLazy = false;

    public Builder(Field field) {
      this.field = field;
      this.columnName = field.getName();
    }

    public Builder columnName(String columnName) {
      this.columnName = columnName;
      return this;
    }

    public Builder isId(boolean isId) {
      this.isId = isId;
      return this;
    }

    public Builder isGeneratedValue(boolean isGeneratedValue) {
      this.isGeneratedValue = isGeneratedValue;
      return this;
    }

    public Builder nullable(boolean nullable) {
      this.nullable = nullable;
      return this;
    }

    public Builder length(int length) {
      this.length = length;
      return this;
    }

    public Builder unique(boolean unique) {
      this.unique = unique;
      return this;
    }

    public Builder relationshipType(RelationshipType type) {
      this.relationshipType = type;
      return this;
    }

    public Builder targetEntity(Class<?> entityClass) {
      this.targetEntity = entityClass;
      return this;
    }

    public Builder mappedBy(String mappedBy) {
      this.mappedBy = mappedBy;
      return this;
    }

    public Builder fetchType(FetchType fetchType) {
      this.fetchType = fetchType;
      return this;
    }

    public Builder isLazy(boolean isLazy) {
      this.isLazy = isLazy;
      return this;
    }

    public FieldMetadata build() {
      return new FieldMetadata(this);
    }
  }
}
