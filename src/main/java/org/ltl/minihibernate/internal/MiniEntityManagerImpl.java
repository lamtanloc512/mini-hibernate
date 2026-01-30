package org.ltl.minihibernate.internal;

import java.sql.Connection;

import org.ltl.minihibernate.api.MiniEntityManager;
import org.ltl.minihibernate.api.MiniTransaction;
import org.ltl.minihibernate.api.MiniTypedQuery;
import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.persist.EntityPersister;
import org.ltl.minihibernate.persist.PersistenceContext;
import org.ltl.minihibernate.session.EntityState;
import org.ltl.minihibernate.sql.SQLGenerator;

import io.vavr.control.Option;
import io.vavr.control.Try;
import jakarta.persistence.CacheRetrieveMode;
import jakarta.persistence.CacheStoreMode;
import jakarta.persistence.ConnectionConsumer;
import jakarta.persistence.ConnectionFunction;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FindOption;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.LockOption;
import jakarta.persistence.Query;
import jakarta.persistence.RefreshOption;
import jakarta.persistence.StoredProcedureQuery;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.TypedQueryReference;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaSelect;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.metamodel.Metamodel;

/**
 * MiniEntityManagerImpl - The ACTUAL implementation.
 * 
 * This is like Hibernate's SessionImpl which implements JPA's EntityManager.
 * 
 * When you call entityManager.persist(), the code ACTUALLY runs here,
 * but you only see the interface in your code.
 * 
 * Package: org.ltl.minihibernate.internal (hidden from API users)
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
  public <T> T getReference(Class<T> entityClass, Object primaryKey) {
    return find(entityClass, primaryKey);
  }

  @Override
  public void lock(Object entity, LockModeType lockMode) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void lock(Object entity, LockModeType lockMode, java.util.Map<String, Object> properties) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void refresh(Object entity) {
    checkOpen();
    EntityMetadata metadata = factory.getEntityMetadata(entity.getClass());
    Object id = metadata.getId(entity);
    if (id == null) {
      throw new IllegalArgumentException("Cannot refresh transient entity");
    }

    Object fresh = Try.of(() -> entityPersister.load(metadata, id))
        .getOrElseThrow(e -> new RuntimeException("Refresh failed", e));

    if (fresh == null) {
      throw new jakarta.persistence.EntityNotFoundException("Entity not found in DB: " + id);
    }

    copyState(fresh, entity, metadata);
    persistenceContext.addEntity(entity, metadata, EntityState.MANAGED);
  }

  @Override
  public void refresh(Object entity, java.util.Map<String, Object> properties) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void refresh(Object entity, LockModeType lockMode) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void refresh(Object entity, LockModeType lockMode,
      java.util.Map<String, Object> properties) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void detach(Object entity) {
    checkOpen();
    EntityMetadata metadata = factory.getEntityMetadata(entity.getClass());
    Object id = metadata.getId(entity);
    if (id != null) {
      persistenceContext.removeEntity(entity.getClass(), id);
    }
  }

  @Override
  public LockModeType getLockMode(Object entity) {
    return LockModeType.NONE;
  }

  @Override
  public void setProperty(String propertyName, Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public java.util.Map<String, Object> getProperties() {
    return java.util.Collections.emptyMap();
  }

  @Override
  public Query createQuery(String qlString) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> TypedQuery<T> createQuery(
      CriteriaQuery<T> criteriaQuery) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Query createQuery(CriteriaUpdate<?> updateQuery) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Query createQuery(CriteriaDelete<?> deleteQuery) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> TypedQuery<T> createQuery(String qlString, Class<T> resultClass) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Query createNamedQuery(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> TypedQuery<T> createNamedQuery(String name, Class<T> resultClass) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Query createNativeQuery(String sqlString) {
    checkOpen();
    return new MiniNativeQueryImpl(sqlString, connection);
  }

  @Override
  public <T> Query createNativeQuery(String sqlString, Class<T> resultClass) {
    return createNativeQuery(sqlString);
  }

  @Override
  public Query createNativeQuery(String sqlString, String resultSetMapping) {
    throw new UnsupportedOperationException();
  }

  @Override
  public StoredProcedureQuery createNamedStoredProcedureQuery(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public StoredProcedureQuery createStoredProcedureQuery(String procedureName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public StoredProcedureQuery createStoredProcedureQuery(String procedureName,
      Class<?>... resultClasses) {
    throw new UnsupportedOperationException();
  }

  @Override
  public StoredProcedureQuery createStoredProcedureQuery(String procedureName,
      String... resultSetMappings) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void joinTransaction() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isJoinedToTransaction() {
    return transaction != null && transaction.isActive();
  }

  @SuppressWarnings("unchecked")
  @Override
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
  public Object getDelegate() {
    return this;
  }

  @Override
  public EntityManagerFactory getEntityManagerFactory() {
    return factory;
  }

  @Override
  public CriteriaBuilder getCriteriaBuilder() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Metamodel getMetamodel() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> EntityGraph<T> createEntityGraph(Class<T> rootType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EntityGraph<?> createEntityGraph(String graphName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EntityGraph<?> getEntityGraph(String graphName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> java.util.List<EntityGraph<? super T>> getEntityGraphs(Class<T> entityClass) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setFlushMode(FlushModeType flushMode) {
  }

  @Override
  public FlushModeType getFlushMode() {
    return FlushModeType.AUTO;
  }

  // New methods in JPA 3.2+
  @Override
  public void setCacheStoreMode(CacheStoreMode cacheStoreMode) {
  }

  @Override
  public CacheStoreMode getCacheStoreMode() {
    return CacheStoreMode.USE;
  }

  @Override
  public void setCacheRetrieveMode(CacheRetrieveMode cacheRetrieveMode) {
  }

  @Override
  public CacheRetrieveMode getCacheRetrieveMode() {
    return CacheRetrieveMode.USE;
  }

  @Override
  public <T> T find(Class<T> entityClass, Object primaryKey, FindOption... options) {
    return find(entityClass, primaryKey);
  }

  @Override
  public <T> T find(EntityGraph<T> entityGraph, Object primaryKey,
      FindOption... options) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void refresh(Object entity, RefreshOption... options) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void lock(Object entity, LockModeType lockMode,
      LockOption... lockOptions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> TypedQuery<T> createQuery(
      CriteriaSelect<T> criteriaQuery) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> TypedQuery<T> createQuery(TypedQueryReference<T> queryReference) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T getReference(T entity) {
    return entity;
  }

  @Override
  public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode) {
    return find(entityClass, primaryKey);
  }

  @Override
  public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode,
      java.util.Map<String, Object> properties) {
    return find(entityClass, primaryKey);
  }

  @Override
  public <T> T find(Class<T> entityClass, Object primaryKey, java.util.Map<String, Object> properties) {
    return find(entityClass, primaryKey);
  }

  @Override
  public <C, T> T callWithConnection(ConnectionFunction<C, T> function) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <C> void runWithConnection(ConnectionConsumer<C> consumer) {
    throw new UnsupportedOperationException();
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
