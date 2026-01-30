package org.ltl.minihibernate.internal;

import jakarta.persistence.metamodel.EmbeddableType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.Type;
import java.util.Set;
import java.util.stream.Collectors;

public class MiniMetamodelImpl implements Metamodel {

  private final io.vavr.collection.Map<Class<?>, ?> entityMetadataMap;

  public MiniMetamodelImpl(io.vavr.collection.Map<Class<?>, ?> entityMetadataMap) {
    this.entityMetadataMap = entityMetadataMap;
  }

  @Override
  public <X> EntityType<X> entity(Class<X> cls) {
    return createEntityType(cls);
  }

  public EntityType<?> entity(String name) {
    return null;
  }

  @Override
  public <X> ManagedType<X> managedType(Class<X> cls) {
    if (entityMetadataMap.containsKey(cls)) {
      return (ManagedType<X>) entity(cls);
    }
    return createManagedType(cls);
  }

  public ManagedType<?> managedType(String name) {
    return null;
  }

  @Override
  public <X> EmbeddableType<X> embeddable(Class<X> cls) {
    return null;
  }

  public EmbeddableType<?> embeddable(String name) {
    return null;
  }

  @Override
  public Set<ManagedType<?>> getManagedTypes() {
    return entityMetadataMap.keySet()
        .toJavaSet()
        .stream()
        .map(cls -> (ManagedType<?>) entity(cls))
        .collect(Collectors.toSet());
  }

  public <X> ManagedType<X> getManagedType(Class<X> cls) {
    return managedType(cls);
  }

  @Override
  public Set<EntityType<?>> getEntities() {
    return entityMetadataMap.keySet()
        .toJavaSet()
        .stream()
        .map(this::createEntityType)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<EmbeddableType<?>> getEmbeddables() {
    return java.util.Collections.emptySet();
  }

  @SuppressWarnings("unchecked")
  private <X> ManagedType<X> createManagedType(Class<X> cls) {
    return (ManagedType<X>) java.lang.reflect.Proxy.newProxyInstance(
        ManagedType.class.getClassLoader(),
        new Class<?>[] { ManagedType.class },
        (proxy, method, args) -> {
          if (method.getName().equals("getJavaType"))
            return cls;
          if (method.getName().equals("getPersistenceType"))
            return jakarta.persistence.metamodel.Type.PersistenceType.ENTITY;
          if (method.getName().equals("getAttributes"))
            return java.util.Collections.emptySet();
          if (method.getName().equals("getSingularAttributes"))
            return java.util.Collections.emptySet();
          if (method.getName().equals("getDeclaredSingularAttributes"))
            return java.util.Collections.emptySet();
          if (method.getName().equals("hashCode"))
            return System.identityHashCode(proxy);
          if (method.getName().equals("equals"))
            return proxy == args[0];
          if (method.getName().equals("toString"))
            return "ManagedType[" + cls.getName() + "]";
          return null;
        });
  }

  @SuppressWarnings("unchecked")
  private <X> EntityType<X> createEntityType(Class<X> cls) {
    io.vavr.control.Option<org.ltl.minihibernate.metadata.EntityMetadata> metadataOpt = (io.vavr.control.Option<org.ltl.minihibernate.metadata.EntityMetadata>) entityMetadataMap
        .get(cls);
    if (metadataOpt.isEmpty()) {
      System.out.println("WARNING: No metadata found for " + cls.getName());
    }
    return (EntityType<X>) java.lang.reflect.Proxy.newProxyInstance(
        EntityType.class.getClassLoader(),
        new Class<?>[] { EntityType.class },
        (proxy, method, args) -> {
          if (method.getName().equals("getJavaType"))
            return cls;
          if (method.getName().equals("getName"))
            return cls.getSimpleName();
          if (method.getName().equals("getPersistenceType"))
            return jakarta.persistence.metamodel.Type.PersistenceType.ENTITY;

          jakarta.persistence.metamodel.SingularAttribute<X, ?> idAttr = metadataOpt.map(m -> createIdAttribute(cls, m))
              .getOrNull();
          java.util.Set<jakarta.persistence.metamodel.Attribute<? super X, ?>> attrs = idAttr != null
              ? java.util.Collections.singleton(idAttr)
              : java.util.Collections.emptySet();
          java.util.Set<jakarta.persistence.metamodel.SingularAttribute<? super X, ?>> sAttrs = idAttr != null
              ? java.util.Collections.singleton(idAttr)
              : java.util.Collections.emptySet();

          if (method.getName().equals("getAttributes"))
            return attrs;
          if (method.getName().equals("getSingularAttributes"))
            return sAttrs;
          if (method.getName().equals("getDeclaredSingularAttributes"))
            return sAttrs;
          if (method.getName().equals("getId"))
            return idAttr;
          if (method.getName().equals("getDeclaredId"))
            return idAttr;
          if (method.getName().equals("getIdType")) {
            return idAttr != null ? idAttr.getType() : null;
          }
          if (method.getName().equals("hasSingleIdAttribute"))
            return true;
          if (method.getName().equals("hashCode"))
            return System.identityHashCode(proxy);
          if (method.getName().equals("equals"))
            return proxy == args[0];
          if (method.getName().equals("toString"))
            return "EntityType[" + cls.getName() + "]";
          return null;
        });
  }

  @SuppressWarnings("unchecked")
  private <X, T> jakarta.persistence.metamodel.SingularAttribute<X, T> createIdAttribute(Class<X> cls,
      org.ltl.minihibernate.metadata.EntityMetadata metadata) {
    return (jakarta.persistence.metamodel.SingularAttribute<X, T>) java.lang.reflect.Proxy.newProxyInstance(
        jakarta.persistence.metamodel.SingularAttribute.class.getClassLoader(),
        new Class<?>[] { jakarta.persistence.metamodel.SingularAttribute.class },
        (proxy, method, args) -> {
          if (method.getName().equals("getName"))
            return metadata.getIdField().getFieldName();
          if (method.getName().equals("getJavaType"))
            return metadata.getIdField().getJavaType();
          if (method.getName().equals("getPersistentAttributeType"))
            return jakarta.persistence.metamodel.Attribute.PersistentAttributeType.BASIC;
          if (method.getName().equals("isId"))
            return true;
          if (method.getName().equals("getType")) {
            return createBasicType(metadata.getIdField().getJavaType());
          }
          if (method.getName().equals("getDeclaringType"))
            return createEntityType(cls);
          if (method.getName().equals("getJavaMember"))
            return metadata.getIdField().getField();

          if (method.getName().equals("hashCode"))
            return System.identityHashCode(proxy);
          if (method.getName().equals("equals"))
            return proxy == args[0];
          return null;
        });
  }

  private <T> Type<T> createBasicType(Class<T> cls) {
    return (Type<T>) java.lang.reflect.Proxy.newProxyInstance(
        Type.class.getClassLoader(),
        new Class<?>[] { Type.class },
        (proxy, method, args) -> {
          if (method.getName().equals("getJavaType"))
            return cls;
          if (method.getName().equals("getPersistenceType"))
            return Type.PersistenceType.BASIC;
          if (method.getName().equals("toString"))
            return "BasicType[" + cls.getName() + "]";
          return null;
        });
  }

}
