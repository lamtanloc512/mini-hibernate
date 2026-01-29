package org.ltl.minihibernate.metadata;

import java.lang.reflect.Field;

/**
 * Holds metadata about a mapped field/column.
 * 
 * This class stores information extracted from @Column and @Id annotations.
 */
public class FieldMetadata {

  private final Field field;
  private final String columnName;
  private final boolean isId;
  private final boolean isGeneratedValue;
  private final boolean nullable;
  private final int length;
  private final boolean unique;

  private FieldMetadata(Builder builder) {
    this.field = builder.field;
    this.columnName = builder.columnName;
    this.isId = builder.isId;
    this.isGeneratedValue = builder.isGeneratedValue;
    this.nullable = builder.nullable;
    this.length = builder.length;
    this.unique = builder.unique;

    // Make field accessible for reflection
    this.field.setAccessible(true);
  }

  public Field getField() {
    return field;
  }

  public String getFieldName() {
    return field.getName();
  }

  public String getColumnName() {
    return columnName;
  }

  public Class<?> getJavaType() {
    return field.getType();
  }

  public boolean isId() {
    return isId;
  }

  public boolean isGeneratedValue() {
    return isGeneratedValue;
  }

  public boolean isNullable() {
    return nullable;
  }

  public int getLength() {
    return length;
  }

  public boolean isUnique() {
    return unique;
  }

  /**
   * Gets the value of this field from the given entity.
   */
  public Object getValue(Object entity) {
    try {
      return field.get(entity);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Cannot access field: " + field.getName(), e);
    }
  }

  /**
   * Sets the value of this field on the given entity.
   */
  public void setValue(Object entity, Object value) {
    try {
      field.set(entity, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Cannot set field: " + field.getName(), e);
    }
  }

  @Override
  public String toString() {
    return "FieldMetadata{" +
        "field=" + field.getName() +
        ", columnName='" + columnName + '\'' +
        ", isId=" + isId +
        ", javaType=" + getJavaType().getSimpleName() +
        '}';
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

    public Builder(Field field) {
      this.field = field;
      this.columnName = field.getName(); // default to field name
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

    public FieldMetadata build() {
      return new FieldMetadata(this);
    }
  }
}
