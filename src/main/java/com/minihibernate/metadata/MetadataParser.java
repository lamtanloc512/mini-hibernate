package com.minihibernate.metadata;

import com.minihibernate.annotation.Column;
import com.minihibernate.annotation.Entity;
import com.minihibernate.annotation.GeneratedValue;
import com.minihibernate.annotation.Id;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses entity classes and extracts metadata from annotations.
 * 
 * This is a key component that uses Java Reflection to read
 * @Entity, @Id, @Column, etc. annotations and build EntityMetadata.
 */
public class MetadataParser {
    
    private final Map<Class<?>, EntityMetadata> cache = new HashMap<>();
    
    /**
     * Parses an entity class and returns its metadata.
     * Results are cached for performance.
     * 
     * @param entityClass The class to parse
     * @return EntityMetadata containing all mapping information
     * @throws IllegalArgumentException if class is not a valid entity
     */
    public EntityMetadata parse(Class<?> entityClass) {
        // Check cache first
        if (cache.containsKey(entityClass)) {
            return cache.get(entityClass);
        }
        
        // Validate @Entity annotation
        Entity entityAnnotation = entityClass.getAnnotation(Entity.class);
        if (entityAnnotation == null) {
            throw new IllegalArgumentException(
                "Class " + entityClass.getName() + " is not annotated with @Entity"
            );
        }
        
        // Determine table name
        String tableName = entityAnnotation.table();
        if (tableName.isEmpty()) {
            tableName = entityClass.getSimpleName().toLowerCase();
        }
        
        // Build entity metadata
        EntityMetadata.Builder builder = EntityMetadata.builder(entityClass)
                .tableName(tableName);
        
        // Parse all declared fields
        for (Field field : entityClass.getDeclaredFields()) {
            FieldMetadata fieldMetadata = parseField(field);
            
            if (fieldMetadata != null) {
                if (fieldMetadata.isId()) {
                    builder.idField(fieldMetadata);
                } else {
                    builder.addColumn(fieldMetadata);
                }
            }
        }
        
        EntityMetadata metadata = builder.build();
        cache.put(entityClass, metadata);
        return metadata;
    }
    
    /**
     * Parses a single field and extracts its metadata.
     * 
     * @param field The field to parse
     * @return FieldMetadata or null if field should be ignored
     */
    private FieldMetadata parseField(Field field) {
        // Skip static and transient fields
        int modifiers = field.getModifiers();
        if (java.lang.reflect.Modifier.isStatic(modifiers) ||
            java.lang.reflect.Modifier.isTransient(modifiers)) {
            return null;
        }
        
        Id idAnnotation = field.getAnnotation(Id.class);
        Column columnAnnotation = field.getAnnotation(Column.class);
        GeneratedValue generatedValue = field.getAnnotation(GeneratedValue.class);
        
        // If no annotations, still include field with default settings
        // (This mimics JPA's default behavior)
        
        FieldMetadata.Builder builder = FieldMetadata.builder(field);
        
        // Handle @Id
        if (idAnnotation != null) {
            builder.isId(true);
        }
        
        // Handle @GeneratedValue
        if (generatedValue != null) {
            builder.isGeneratedValue(true);
        }
        
        // Handle @Column
        if (columnAnnotation != null) {
            String columnName = columnAnnotation.name();
            if (!columnName.isEmpty()) {
                builder.columnName(columnName);
            }
            builder.nullable(columnAnnotation.nullable());
            builder.length(columnAnnotation.length());
            builder.unique(columnAnnotation.unique());
        }
        
        return builder.build();
    }
    
    /**
     * Clears the metadata cache.
     * Useful for testing.
     */
    public void clearCache() {
        cache.clear();
    }
}
