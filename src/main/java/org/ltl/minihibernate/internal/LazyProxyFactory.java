package org.ltl.minihibernate.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.metadata.FieldMetadata;
import org.ltl.minihibernate.metadata.MetadataParser;

/**
 * Factory for creating lazy-loading proxies.
 * Uses Java's Dynamic Proxy to intercept method calls and load data on demand.
 */
public class LazyProxyFactory {

    private final MetadataParser metadataParser;
    private final BiFunction<Class<?>, Object, Object> entityLoader;
    private final Map<Class<?>, EntityMetadata> metadataCache;

    public LazyProxyFactory(MetadataParser metadataParser,
                           BiFunction<Class<?>, Object, Object> entityLoader,
                           Map<Class<?>, EntityMetadata> metadataCache) {
        this.metadataParser = metadataParser;
        this.entityLoader = entityLoader;
        this.metadataCache = metadataCache;
    }

    /**
     * Create a lazy-loading proxy for a ManyToOne relationship.
     *
     * @param targetClass The entity class to load
     * @param ownerEntity The entity that owns this relationship (for foreign key)
     * @param foreignKeyField The field containing the foreign key value
     * @return A proxy that loads the entity on first access
     */
    @SuppressWarnings("unchecked")
    public <T> T createManyToOneProxy(Class<T> targetClass, Object ownerEntity,
                                      FieldMetadata foreignKeyField) {
        Class<?> ownerClass = ownerEntity.getClass();
        EntityMetadata ownerMetadata = metadataCache.computeIfAbsent(ownerClass, metadataParser::parse);

        return (T) Proxy.newProxyInstance(
            targetClass.getClassLoader(),
            new Class<?>[] { targetClass },
            new ManyToOneInvocationHandler(
                targetClass, ownerEntity, foreignKeyField, ownerMetadata
            )
        );
    }

    /**
     * Create a lazy-loading proxy for a OneToMany/ManyToMany collection.
     *
     * @param elementClass The element class of the collection
     * @param ownerEntity The entity that owns this collection
     * @param collectionField The field metadata for the collection
     * @return A proxy list that loads the collection on first access
     */
    @SuppressWarnings("unchecked")
    public <T extends Collection<E>, E> T createCollectionProxy(Class<T> collectionInterface,
                                                                 Class<E> elementClass,
                                                                 Object ownerEntity,
                                                                 FieldMetadata collectionField) {
        // Determine appropriate collection interface
        Class<?> proxyInterface;
        if (List.class.isAssignableFrom(collectionInterface)) {
            proxyInterface = List.class;
        } else if (Collection.class.isAssignableFrom(collectionInterface)) {
            proxyInterface = Collection.class;
        } else {
            proxyInterface = Collection.class;
        }

        return (T) Proxy.newProxyInstance(
            collectionInterface.getClassLoader(),
            new Class<?>[] { proxyInterface },
            new OneToManyInvocationHandler(
                elementClass, ownerEntity, collectionField
            )
        );
    }

    /**
     * Create a proxy that wraps an entity and intercepts method calls.
     */
    @SuppressWarnings("unchecked")
    public <T> T createEntityProxy(T entity, Class<T> entityClass) {
        return (T) Proxy.newProxyInstance(
            entityClass.getClassLoader(),
            new Class<?>[] { entityClass },
            new EntityInvocationHandler(entity, entityClass)
        );
    }

    /**
     * InvocationHandler for ManyToOne lazy loading.
     */
    private class ManyToOneInvocationHandler implements InvocationHandler {
        private final Class<?> targetClass;
        private final Object ownerEntity;
        private final FieldMetadata foreignKeyField;
        private final EntityMetadata ownerMetadata;
        private volatile Object target;
        private volatile boolean loaded;

        ManyToOneInvocationHandler(Class<?> targetClass, Object ownerEntity,
                                   FieldMetadata foreignKeyField, EntityMetadata ownerMetadata) {
            this.targetClass = targetClass;
            this.ownerEntity = ownerEntity;
            this.foreignKeyField = foreignKeyField;
            this.ownerMetadata = ownerMetadata;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (loaded) {
                return method.invoke(target, args);
            }

            // Check if it's Object methods (toString, hashCode, equals)
            String methodName = method.getName();
          switch (methodName) {
            case "toString" -> {
              return targetClass.getSimpleName() + " (lazy proxy)";
            }
            case "hashCode" -> {
              return System.identityHashCode(proxy);
            }
            case "equals" -> {
              return proxy == args[0];
            }
          }

          // Load the target entity
            loadTarget();

            return method.invoke(target, args);
        }

        private void loadTarget() {
            if (loaded) return;

            synchronized (this) {
                if (loaded) return;

                // Get the foreign key value from owner entity
                Object foreignKey = foreignKeyField.getValue(ownerEntity);
                if (foreignKey == null) {
                    target = null;
                    loaded = true;
                    return;
                }

                // Load the entity using the entityLoader
                target = entityLoader.apply(targetClass, foreignKey);

                // Register in persistence context if loaded
                if (target != null) {
                    EntityMetadata targetMetadata = metadataCache.computeIfAbsent(
                        targetClass, metadataParser::parse);
                    // The caller should register the entity
                }

                loaded = true;
            }
        }
    }

    /**
     * InvocationHandler for OneToMany collection lazy loading.
     */
    private static class OneToManyInvocationHandler implements InvocationHandler {
        private final Class<?> elementClass;
        private final Object ownerEntity;
        private final FieldMetadata collectionField;
        private volatile List<?> target;
        private volatile boolean loaded;

        OneToManyInvocationHandler(Class<?> elementClass, Object ownerEntity,
                                   FieldMetadata collectionField) {
            this.elementClass = elementClass;
            this.ownerEntity = ownerEntity;
            this.collectionField = collectionField;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (loaded) {
                return method.invoke(target, args);
            }

            String methodName = method.getName();

            // Methods that trigger loading
            if (methodName.equals("iterator") ||
                methodName.equals("size") ||
                methodName.equals("isEmpty") ||
                methodName.equals("contains") ||
                methodName.equals("toArray") ||
                methodName.equals("stream") ||
                methodName.equals("spliterator")) {
                loadCollection();
                return method.invoke(target, args);
            }

            // toString should work without loading
            if (methodName.equals("toString")) {
                return collectionField.getFieldName() + " (lazy proxy)";
            }
            if (methodName.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (methodName.equals("equals")) {
                return proxy == args[0];
            }

            loadCollection();
            return method.invoke(target, args);
        }

        private void loadCollection() {
            if (loaded) return;

            synchronized (this) {
                if (loaded) return;

                // Query to find related entities
                // SELECT e FROM ElementClass e WHERE e.owner_id = :ownerId
                String ownerIdField = collectionField.getMappedBy();
                if (ownerIdField == null || ownerIdField.isEmpty()) {
                    // Try to find the owning side
                    ownerIdField = "id";
                }

                // For now, create an empty list as placeholder
                // In a full implementation, this would query the database
                target = List.of();
                loaded = true;
            }
        }
    }

    /**
     * Basic InvocationHandler for entity proxy.
     */
    private static class EntityInvocationHandler implements InvocationHandler {
        private final Object target;
        private final Class<?> targetClass;

        EntityInvocationHandler(Object target, Class<?> targetClass) {
            this.target = target;
            this.targetClass = targetClass;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
