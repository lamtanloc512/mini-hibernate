package org.ltl.minihibernate.internal;

import java.util.Set;

import org.ltl.minihibernate.internal.metamodel.MiniEntityTypeImpl;
import org.ltl.minihibernate.internal.metamodel.MiniManagedTypeImpl;
import org.ltl.minihibernate.internal.metamodel.MiniSingularAttributeImpl;
import org.ltl.minihibernate.internal.metamodel.MiniTypeImpl;
import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.metadata.FieldMetadata;

import io.vavr.collection.HashMap;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EmbeddableType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.Type;

public class MiniMetamodelImpl implements Metamodel {

  private io.vavr.collection.Map<Class<?>, MiniEntityTypeImpl<?>> entities = HashMap.empty();
  private io.vavr.collection.Map<Class<?>, MiniManagedTypeImpl<?>> managedTypes = HashMap.empty();

  public MiniMetamodelImpl(io.vavr.collection.Map<Class<?>, EntityMetadata> entityMetadataMap) {
    initialize(entityMetadataMap);
  }

  private void initialize(io.vavr.collection.Map<Class<?>, EntityMetadata> entityMetadataMap) {
    // 1. Create all EntityTypes
    entityMetadataMap.forEach((cls, metadata) -> {
      MiniEntityTypeImpl<?> entityType = new MiniEntityTypeImpl<>(cls, metadata.getTableName());
      entities = entities.put(cls, entityType);
      managedTypes = managedTypes.put(cls, entityType);
    });

    // 2. Initialize Attributes for each EntityType
    entityMetadataMap.forEach((cls, metadata) -> {
      @SuppressWarnings("unchecked")
      MiniEntityTypeImpl<Object> entityType = (MiniEntityTypeImpl<Object>) entities.get(cls).get();
      initializeAttributes(entityType, metadata);
    });
  }

  private <X> void initializeAttributes(MiniEntityTypeImpl<X> entityType, EntityMetadata metadata) {
    // ID Attribute
    FieldMetadata idField = metadata.getIdField();
    if (idField != null) {
      MiniSingularAttributeImpl<X, Object> idAttr = createSingularAttribute(entityType, idField, true);
      entityType.setIdAttribute(idAttr);
    }

    // Other columns
    metadata.getColumns().forEach(field -> {
      // TODO: Add support for non-ID attributes
    });
  }

  @SuppressWarnings("unchecked")
  private <X, T> MiniSingularAttributeImpl<X, T> createSingularAttribute(MiniManagedTypeImpl<X> owner,
      FieldMetadata field, boolean isId) {
    Class<T> attrType = (Class<T>) field.getJavaType();
    Type<T> type = new MiniTypeImpl<>(attrType) {
      @Override
      public PersistenceType getPersistenceType() {
        return PersistenceType.BASIC; // Simplify for now
      }
    };

    return new MiniSingularAttributeImpl<>(
        field.getFieldName(),
        attrType,
        owner,
        field.getField(),
        Attribute.PersistentAttributeType.BASIC,
        isId,
        false,
        false,
        type);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <X> EntityType<X> entity(Class<X> cls) {
    return (EntityType<X>) entities.get(cls)
        .getOrElseThrow(() -> new IllegalArgumentException("Not an entity: " + cls));
  }

  @Override
  public EntityType<?> entity(String name) {
    // Very inefficient scan, but standard JPA requires it
    return entities.values()
        .find(e -> e.getJavaType().getSimpleName().equals(name)) // SimpleName approximation
        .map(e -> (EntityType<?>) e)
        .getOrElseThrow(() -> new IllegalArgumentException("EntityType not found: " + name));
  }

  @SuppressWarnings("unchecked")
  @Override
  public <X> ManagedType<X> managedType(Class<X> cls) {
    return (ManagedType<X>) managedTypes.get(cls)
        .getOrElseThrow(() -> new IllegalArgumentException("Not a managed type: " + cls));
  }

  public ManagedType<?> managedType(String name) {
    // Very inefficient scan, but standard JPA requires it
    return managedTypes.values()
        .find(m -> m.getJavaType().getSimpleName().equals(name)) // SimpleName approximation
        .map(m -> (ManagedType<?>) m)
        .getOrElseThrow(() -> new IllegalArgumentException("ManagedType not found: " + name));
  }

  @Override
  public <X> EmbeddableType<X> embeddable(Class<X> cls) {
    throw new IllegalArgumentException("Embeddables not supported");
  }

  public EmbeddableType<?> embeddable(String name) {
    throw new IllegalArgumentException("Embeddables not supported");
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Override
  public Set<ManagedType<?>> getManagedTypes() {
    return (Set) managedTypes.values().toJavaSet();
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Override
  public Set<EntityType<?>> getEntities() {
    return (Set) entities.values().toJavaSet();
  }

  @Override
  public Set<EmbeddableType<?>> getEmbeddables() {
    return java.util.Collections.emptySet();
  }
}
