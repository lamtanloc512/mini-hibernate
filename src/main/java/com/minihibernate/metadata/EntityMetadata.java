package com.minihibernate.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds metadata about a mapped entity class.
 * 
 * This class stores information extracted from @Entity annotation
 * and aggregates all field metadata.
 */
public class EntityMetadata {
    
    private final Class<?> entityClass;
    private final String tableName;
    private final FieldMetadata idField;
    private final List<FieldMetadata> columns;
    
    private EntityMetadata(Builder builder) {
        this.entityClass = builder.entityClass;
        this.tableName = builder.tableName;
        this.idField = builder.idField;
        this.columns = Collections.unmodifiableList(new ArrayList<>(builder.columns));
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
    
    /**
     * Returns all columns including the ID field.
     */
    public List<FieldMetadata> getAllColumns() {
        List<FieldMetadata> all = new ArrayList<>();
        all.add(idField);
        all.addAll(columns);
        return all;
    }
    
    /**
     * Returns non-ID columns only.
     */
    public List<FieldMetadata> getColumns() {
        return columns;
    }
    
    /**
     * Creates a new instance of the entity using default constructor.
     */
    public Object newInstance() {
        try {
            return entityClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot create instance of: " + entityClass.getName(), e);
        }
    }
    
    /**
     * Gets the ID value from an entity instance.
     */
    public Object getId(Object entity) {
        return idField.getValue(entity);
    }
    
    /**
     * Sets the ID value on an entity instance.
     */
    public void setId(Object entity, Object id) {
        idField.setValue(entity, id);
    }
    
    @Override
    public String toString() {
        return "EntityMetadata{" +
                "entityClass=" + entityClass.getSimpleName() +
                ", tableName='" + tableName + '\'' +
                ", idField=" + idField.getColumnName() +
                ", columns=" + columns.size() +
                '}';
    }
    
    public static Builder builder(Class<?> entityClass) {
        return new Builder(entityClass);
    }
    
    public static class Builder {
        private final Class<?> entityClass;
        private String tableName;
        private FieldMetadata idField;
        private List<FieldMetadata> columns = new ArrayList<>();
        
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
        
        public EntityMetadata build() {
            if (idField == null) {
                throw new IllegalStateException("Entity must have an @Id field");
            }
            return new EntityMetadata(this);
        }
    }
}
