package com.minihibernate.internal;

import com.minihibernate.api.MiniEntityManager;
import com.minihibernate.api.MiniTransaction;
import com.minihibernate.api.MiniTypedQuery;
import com.minihibernate.metadata.EntityMetadata;
import com.minihibernate.persist.EntityPersister;
import com.minihibernate.persist.PersistenceContext;
import com.minihibernate.session.EntityState;
import com.minihibernate.sql.SQLGenerator;
import io.vavr.control.Option;
import io.vavr.control.Try;

import java.sql.Connection;

/**
 * MiniEntityManagerImpl - The ACTUAL implementation.
 * 
 * This is like Hibernate's SessionImpl which implements JPA's EntityManager.
 * 
 * When you call entityManager.persist(), the code ACTUALLY runs here,
 * but you only see the interface in your code.
 * 
 * Package: com.minihibernate.internal (hidden from API users)
 */
public class MiniEntityManagerImpl implements MiniEntityManager {
    
    private final Connection connection;
    private final MiniEntityManagerFactoryImpl factory;
    private final PersistenceContext persistenceContext;
    private final EntityPersister entityPersister;
    private final SQLGenerator sqlGenerator;
    private MiniTransactionImpl transaction;
    private boolean open = true;
    
    // Package-private constructor - only Factory can create
    MiniEntityManagerImpl(Connection connection, MiniEntityManagerFactoryImpl factory) {
        this.connection = connection;
        this.factory = factory;
        this.persistenceContext = new PersistenceContext();
        this.sqlGenerator = new SQLGenerator();
        this.entityPersister = new EntityPersister(connection, sqlGenerator);
    }
    
    @Override
    public void persist(Object entity) {
        checkOpen();
        EntityMetadata metadata = factory.getEntityMetadata(entity.getClass());
        
        Object id = metadata.getId(entity);
        if (id != null && persistenceContext.contains(entity.getClass(), id)) {
            return;
        }
        
        persistenceContext.addEntity(entity, metadata, EntityState.MANAGED);
        persistenceContext.scheduleInsert(entity);
    }
    
    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey) {
        checkOpen();
        EntityMetadata metadata = factory.getEntityMetadata(entityClass);
        
        // Check first-level cache
        Option<Object> cached = persistenceContext.getEntity(entityClass, primaryKey);
        if (cached.isDefined()) {
            return entityClass.cast(cached.get());
        }
        
        // Load from database
        return Try.of(() -> entityPersister.load(metadata, primaryKey))
                .map(entity -> {
                    if (entity != null) {
                        persistenceContext.addEntity(entity, metadata, EntityState.MANAGED);
                    }
                    return entityClass.cast(entity);
                })
                .getOrElseThrow(e -> new RuntimeException("Failed to find entity", e));
    }
    
    @Override
    public void remove(Object entity) {
        checkOpen();
        EntityMetadata metadata = factory.getEntityMetadata(entity.getClass());
        Object id = metadata.getId(entity);
        
        if (id == null) {
            throw new IllegalArgumentException("Cannot remove transient entity");
        }
        
        persistenceContext.markRemoved(entity);
        persistenceContext.scheduleDelete(entity);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T merge(T entity) {
        checkOpen();
        EntityMetadata metadata = factory.getEntityMetadata(entity.getClass());
        Object id = metadata.getId(entity);
        
        if (id == null) {
            persist(entity);
            return entity;
        }
        
        Option<Object> existing = persistenceContext.getEntity(entity.getClass(), id);
        if (existing.isDefined()) {
            copyState(entity, existing.get(), metadata);
            return (T) existing.get();
        }
        
        T managed = find((Class<T>) entity.getClass(), id);
        if (managed != null) {
            copyState(entity, managed, metadata);
        } else {
            persist(entity);
            managed = entity;
        }
        return managed;
    }
    
    @Override
    public void flush() {
        checkOpen();
        
        // Process inserts
        persistenceContext.getInsertQueue().forEach(entity -> {
            EntityMetadata metadata = factory.getEntityMetadata(entity.getClass());
            Try.run(() -> {
                Object generatedId = entityPersister.insert(metadata, entity);
                if (generatedId != null) {
                    metadata.setId(entity, generatedId);
                    persistenceContext.updateEntityKey(entity, metadata, generatedId);
                }
            }).getOrElseThrow(e -> new RuntimeException("Insert failed", e));
        });
        
        // Process dirty entities (updates)
        persistenceContext.detectDirtyEntities().forEach(entity -> {
            EntityMetadata metadata = factory.getEntityMetadata(entity.getClass());
            Try.run(() -> entityPersister.update(metadata, entity))
                    .getOrElseThrow(e -> new RuntimeException("Update failed", e));
        });
        
        // Process deletes
        persistenceContext.getDeleteQueue().forEach(entity -> {
            EntityMetadata metadata = factory.getEntityMetadata(entity.getClass());
            Try.run(() -> entityPersister.delete(metadata, entity))
                    .getOrElseThrow(e -> new RuntimeException("Delete failed", e));
        });
        
        persistenceContext.clearActionQueues();
    }
    
    @Override
    public void clear() {
        persistenceContext.clear();
    }
    
    @Override
    public boolean contains(Object entity) {
        EntityMetadata metadata = factory.getEntityMetadata(entity.getClass());
        Object id = metadata.getId(entity);
        return id != null && persistenceContext.contains(entity.getClass(), id);
    }
    
    @Override
    public MiniTransaction getTransaction() {
        if (transaction == null) {
            transaction = new MiniTransactionImpl(connection, this);
        }
        return transaction;
    }
    
    @Override
    public <T> MiniTypedQuery<T> createQuery(Class<T> entityClass) {
        checkOpen();
        return new MiniTypedQueryImpl<>(entityClass, this);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> cls) {
        if (cls.isAssignableFrom(this.getClass())) {
            return (T) this;
        }
        if (cls.isAssignableFrom(Connection.class)) {
            return (T) connection;
        }
        throw new IllegalArgumentException("Cannot unwrap to: " + cls);
    }
    
    @Override
    public boolean isOpen() {
        return open;
    }
    
    @Override
    public void close() {
        if (open) {
            open = false;
            persistenceContext.clear();
            Try.run(connection::close);
        }
    }
    
    // ========== Internal methods for other internal classes ==========
    
    Connection getConnection() {
        return connection;
    }
    
    PersistenceContext getPersistenceContext() {
        return persistenceContext;
    }
    
    EntityMetadata getMetadata(Class<?> entityClass) {
        return factory.getEntityMetadata(entityClass);
    }
    
    private void copyState(Object source, Object target, EntityMetadata metadata) {
        metadata.getColumns().forEach(field -> {
            Object value = field.getValue(source);
            field.setValue(target, value);
        });
    }
    
    private void checkOpen() {
        if (!open) {
            throw new IllegalStateException("EntityManager is closed");
        }
    }
}
