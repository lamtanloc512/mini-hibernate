package org.ltl.minihibernate.metadata;

import jakarta.persistence.*;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses entity classes and extracts metadata from JPA annotations.
 * 
 * Supports:
 * - @Entity, @Table, @Id, @Column, @GeneratedValue
 * - @ManyToOne, @OneToMany, @ManyToMany, @OneToOne
 * - @JoinColumn
 */
public class MetadataParser {

  private final Map<Class<?>, EntityMetadata> cache = new HashMap<>();

  public EntityMetadata parse(Class<?> entityClass) {
    if (cache.containsKey(entityClass)) {
      return cache.get(entityClass);
    }

    Entity entityAnnotation = entityClass.getAnnotation(Entity.class);
    if (entityAnnotation == null) {
      throw new IllegalArgumentException(
          "Class " + entityClass.getName() + " is not annotated with @Entity");
    }

    String tableName = resolveTableName(entityClass, entityAnnotation);

    EntityMetadata.Builder builder = EntityMetadata.builder(entityClass)
        .tableName(tableName);

    for (Field field : entityClass.getDeclaredFields()) {
      FieldMetadata fieldMetadata = parseField(field);

      if (fieldMetadata != null) {
        if (fieldMetadata.isId()) {
          builder.idField(fieldMetadata);
        } else if (fieldMetadata.isRelationship()) {
          builder.addRelationship(fieldMetadata);
        } else {
          builder.addColumn(fieldMetadata);
        }
      }
    }

    EntityMetadata metadata = builder.build();
    cache.put(entityClass, metadata);
    return metadata;
  }

  private String resolveTableName(Class<?> entityClass, Entity entityAnnotation) {
    Table tableAnnotation = entityClass.getAnnotation(Table.class);
    if (tableAnnotation != null && !tableAnnotation.name().isEmpty()) {
      return tableAnnotation.name();
    }

    if (!entityAnnotation.name().isEmpty()) {
      return entityAnnotation.name();
    }

    return entityClass.getSimpleName().toLowerCase();
  }

  private FieldMetadata parseField(Field field) {
    int modifiers = field.getModifiers();
    if (java.lang.reflect.Modifier.isStatic(modifiers) ||
        java.lang.reflect.Modifier.isTransient(modifiers)) {
      return null;
    }

    FieldMetadata.Builder builder = FieldMetadata.builder(field);

    // Parse @Id
    if (field.isAnnotationPresent(Id.class)) {
      builder.isId(true);
    }

    // Parse @GeneratedValue
    if (field.isAnnotationPresent(GeneratedValue.class)) {
      builder.isGeneratedValue(true);
    }

    // Parse @Column
    Column columnAnnotation = field.getAnnotation(Column.class);
    if (columnAnnotation != null) {
      if (!columnAnnotation.name().isEmpty()) {
        builder.columnName(columnAnnotation.name());
      }
      builder.nullable(columnAnnotation.nullable());
      builder.length(columnAnnotation.length());
      builder.unique(columnAnnotation.unique());
    }

    // Parse relationship annotations
    parseRelationships(field, builder);

    return builder.build();
  }

  private void parseRelationships(Field field, FieldMetadata.Builder builder) {
    // @ManyToOne
    ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
    if (manyToOne != null) {
      builder.relationshipType(RelationshipType.MANY_TO_ONE);
      builder.targetEntity(field.getType());
      builder.fetchType(manyToOne.fetch());
      builder.isLazy(manyToOne.fetch() == jakarta.persistence.FetchType.LAZY);
      parseJoinColumn(field, builder);
      return;
    }

    // @OneToMany
    OneToMany oneToMany = field.getAnnotation(OneToMany.class);
    if (oneToMany != null) {
      builder.relationshipType(RelationshipType.ONE_TO_MANY);
      builder.mappedBy(oneToMany.mappedBy());
      builder.targetEntity(extractCollectionType(field));
      builder.fetchType(oneToMany.fetch());
      builder.isLazy(oneToMany.fetch() == jakarta.persistence.FetchType.LAZY);
      return;
    }

    // @ManyToMany
    ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);
    if (manyToMany != null) {
      builder.relationshipType(RelationshipType.MANY_TO_MANY);
      builder.mappedBy(manyToMany.mappedBy());
      builder.targetEntity(extractCollectionType(field));
      builder.fetchType(manyToMany.fetch());
      builder.isLazy(manyToMany.fetch() == jakarta.persistence.FetchType.LAZY);
      return;
    }

    // @OneToOne
    OneToOne oneToOne = field.getAnnotation(OneToOne.class);
    if (oneToOne != null) {
      builder.relationshipType(RelationshipType.ONE_TO_ONE);
      builder.targetEntity(field.getType());
      builder.mappedBy(oneToOne.mappedBy());
      builder.fetchType(oneToOne.fetch());
      builder.isLazy(oneToOne.fetch() == jakarta.persistence.FetchType.LAZY);
      parseJoinColumn(field, builder);
    }
  }

  private void parseJoinColumn(Field field, FieldMetadata.Builder builder) {
    JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
    if (joinColumn != null && !joinColumn.name().isEmpty()) {
      builder.columnName(joinColumn.name());
    } else {
      // Default: fieldName_id
      builder.columnName(field.getName() + "_id");
    }
  }

  private Class<?> extractCollectionType(Field field) {
    if (Collection.class.isAssignableFrom(field.getType())) {
      java.lang.reflect.Type genericType = field.getGenericType();
      if (genericType instanceof ParameterizedType pt) {
        java.lang.reflect.Type[] typeArgs = pt.getActualTypeArguments();
        if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
          return (Class<?>) typeArgs[0];
        }
      }
    }
    return Object.class;
  }

  public void clearCache() {
    cache.clear();
  }
}
