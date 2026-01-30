package org.ltl.minihibernate.internal;

import java.sql.Connection;
import java.util.Collections;
import java.util.*;

import org.ltl.minihibernate.api.MiniEntityManager;
import org.ltl.minihibernate.api.MiniTransaction;
import org.ltl.minihibernate.api.MiniTypedQuery;
import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.persist.EntityPersister;
import org.ltl.minihibernate.persist.PersistenceContext;
import org.ltl.minihibernate.session.EntityState;
import org.ltl.minihibernate.sql.SQLGenerator;

import io.vavr.control.Try;
import jakarta.persistence.CacheRetrieveMode;
import jakarta.persistence.CacheStoreMode;
import jakarta.persistence.ConnectionConsumer;
import jakarta.persistence.ConnectionFunction;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityNotFoundException;
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
 * MiniEntityManagerImpl - Refactored with Vavr and EclipseLink patterns.
 */
public class MiniEntityManagerImpl implements MiniEntityManager {

  private final Connection connection;
  private final MiniEntityManagerFactoryImpl factory;
  private final PersistenceContext persistenceContext;
  private final EntityPersister entityPersister;
  private final SQLGenerator sqlGenerator;
  private MiniTransactionImpl transaction;

  private boolean open = true;
  private Map<String, Object> properties;

  MiniEntityManagerImpl(Connection connection, MiniEntityManagerFactoryImpl factory) {
    this(connection, factory, Collections.emptyMap());
  }

  MiniEntityManagerImpl(Connection connection, MiniEntityManagerFactoryImpl factory, Map<String, Object> properties) {
    this.connection = connection;
    this.factory = factory;
    this.persistenceContext = new PersistenceContext();
    this.sqlGenerator = new SQLGenerator();
    this.entityPersister = new EntityPersister(connection, sqlGenerator, factory::getEntityMetadata);
    this.properties = properties != null ? properties : Collections.emptyMap();
  }

  @Override
  public void persist(Object entity) {
    verifyOpen();
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
    return find(entityClass, primaryKey, null, null);
  }

  @Override
  public <T> T find(Class<T> entityClass, Object primaryKey, Map<String, Object> properties) {
    return find(entityClass, primaryKey, null, properties);
  }

  @Override
  public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode) {
    return find(entityClass, primaryKey, lockMode, null);
  }

  @Override
  public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode, Map<String, Object> properties) {
    verifyOpen();
    // Handle properties/hints if needed in future

    EntityMetadata metadata = factory.getEntityMetadata(entityClass);

    return persistenceContext.getEntity(entityClass, primaryKey)
        .map(entityClass::cast)
        .getOrElse(() -> Try.of(() -> entityPersister.load(metadata, primaryKey, this::find))
            .map(entity -> {
              if (entity != null) {
                persistenceContext.addEntity(entity, metadata, EntityState.MANAGED);
              }
              return entityClass.cast(entity);
            }).getOrElseThrow(e -> new RuntimeException("Failed to find entity", e)));
  }

  @Override
  public void remove(Object entity) {
    verifyOpen();
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
    verifyOpen();
    EntityMetadata metadata = factory.getEntityMetadata(entity.getClass());
    Object id = metadata.getId(entity);

    if (id == null) {
      persist(entity);
      return entity;
    }

    return (T) persistenceContext.getEntity(entity.getClass(), id)
        .map(existing -> {
          copyState(entity, existing, metadata);
          return existing;
        })
        .getOrElse(() -> {
          T managed = find((Class<T>) entity.getClass(), id);
          if (managed != null) {
            copyState(entity, managed, metadata);
          } else {
            persist(entity);
            managed = entity;
          }
          return managed;
        });
  }

  @Override
  public void flush() {
    verifyOpen();

    // Inserts
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

    // Updates
    persistenceContext.detectDirtyEntities().forEach(entity -> {
      EntityMetadata metadata = factory.getEntityMetadata(entity.getClass());
      Try.run(() -> entityPersister.update(metadata, entity))
          .getOrElseThrow(e -> new RuntimeException("Update failed", e));
    });

    // Deletes
    persistenceContext.getDeleteQueue().forEach(entity -> {
      EntityMetadata metadata = factory.getEntityMetadata(entity.getClass());
      Try.run(() -> entityPersister.delete(metadata, entity))
          .getOrElseThrow(e -> new RuntimeException("Delete failed", e));
    });

    persistenceContext.clearActionQueues();
  }

  @Override
  public void clear() {
    verifyOpen();
    persistenceContext.clear();
  }

  @Override
  public boolean contains(Object entity) {
    verifyOpen(); // Standard JPA doesn't strongly require checkOpen here but it's good practice
    EntityMetadata metadata = factory.getEntityMetadata(entity.getClass());
    Object id = metadata.getId(entity);
    return id != null && persistenceContext.contains(entity.getClass(), id);
  }

  @Override
  public MiniTransaction getTransaction() {
    verifyOpen();
    if (transaction == null) {
      transaction = new MiniTransactionImpl(connection, this);
    }
    return transaction;
  }

  @Override
  public void close() {
    if (open) {
      open = false;
      persistenceContext.clear();
      Try.run(connection::close);
    }
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  private void verifyOpen() {
    if (!open) {
      throw new IllegalStateException("EntityManager is closed");
    }
  }

  @Override
  public <T> MiniTypedQuery<T> createQuery(Class<T> entityClass) {
    verifyOpen();
    return new MiniTypedQueryImpl<>(entityClass, this);
  }

  @Override
  public <T> TypedQuery<T> createQuery(String qlString, Class<T> resultClass) {
    verifyOpen();
    return new MiniTypedQueryImpl<>(qlString, resultClass, this);
  }

  @Override
  public <T> T getReference(Class<T> entityClass, Object primaryKey) {
    verifyOpen();
    return find(entityClass, primaryKey); // Logic similar to EclipseLink for now
  }

  // --- Stubs / Unsupported ---

  @Override
  public void lock(Object entity, LockModeType lockMode) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void lock(Object entity, LockModeType lockMode, Map<String, Object> properties) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void refresh(Object entity) {
    verifyOpen();
    EntityMetadata metadata = factory.getEntityMetadata(entity.getClass());
    Object id = metadata.getId(entity);
    if (id == null)
      throw new IllegalArgumentException("Cannot refresh transient entity");

    Object fresh = Try.of(() -> entityPersister.load(metadata, id, this::find))
        .getOrElseThrow(e -> new RuntimeException("Refresh failed", e));

    if (fresh == null)
      throw new EntityNotFoundException("Entity not found in DB: " + id);

    copyState(fresh, entity, metadata);
    persistenceContext.addEntity(entity, metadata, EntityState.MANAGED);
  }

  @Override
  public void refresh(Object entity, Map<String, Object> properties) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void refresh(Object entity, LockModeType lockMode) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void refresh(Object entity, LockModeType lockMode, Map<String, Object> properties) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void detach(Object entity) {
    verifyOpen();
    EntityMetadata metadata = factory.getEntityMetadata(entity.getClass());
    Object id = metadata.getId(entity);
    if (id != null)
      persistenceContext.removeEntity(entity.getClass(), id);
  }

  @Override
  public LockModeType getLockMode(Object entity) {
    return LockModeType.NONE;
  }

  @Override
  public void setProperty(String propertyName, Object value) {
    verifyOpen();
    // In real implementation we would update 'properties' map or specific flags
    throw new UnsupportedOperationException("setProperty not implemented");
  }

  @Override
  public Map<String, Object> getProperties() {
    return properties;
  }

  @Override
  public Query createQuery(String qlString) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> TypedQuery<T> createQuery(CriteriaQuery<T> criteriaQuery) {
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
  public Query createNamedQuery(String name) {
    throw new IllegalArgumentException("Named query not found: " + name);
  }

  @Override
  public <T> TypedQuery<T> createNamedQuery(String name, Class<T> resultClass) {
    throw new IllegalArgumentException("Named query not found: " + name);
  }

  @Override
  public Query createNativeQuery(String sqlString) {
    verifyOpen();
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
  public StoredProcedureQuery createStoredProcedureQuery(String procedureName, Class<?>... resultClasses) {
    throw new UnsupportedOperationException();
  }

  @Override
  public StoredProcedureQuery createStoredProcedureQuery(String procedureName, String... resultSetMappings) {
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
    if (cls.isAssignableFrom(this.getClass()))
      return (T) this;
    if (cls.isAssignableFrom(Connection.class))
      return (T) connection;
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
  public <T> T find(EntityGraph<T> entityGraph, Object primaryKey, FindOption... options) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void refresh(Object entity, RefreshOption... options) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void lock(Object entity, LockModeType lockMode, LockOption... lockOptions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> TypedQuery<T> createQuery(CriteriaSelect<T> criteriaQuery) {
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
  public <C, T> T callWithConnection(ConnectionFunction<C, T> function) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <C> void runWithConnection(ConnectionConsumer<C> consumer) {
    throw new UnsupportedOperationException();
  }

  // --- Internals ---

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
    metadata.getUpdatableFields().forEach(field -> {
      Object value = field.getValue(source);
      field.setValue(target, value);
    });
  }
}
