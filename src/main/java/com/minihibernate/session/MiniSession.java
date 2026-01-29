package com.minihibernate.session;

import com.minihibernate.metadata.EntityMetadata;
import com.minihibernate.persist.EntityPersister;
import com.minihibernate.persist.PersistenceContext;
import com.minihibernate.query.MiniQuery;
import com.minihibernate.sql.SQLGenerator;
import io.vavr.control.Option;
import io.vavr.control.Try;

import java.io.Closeable;
import java.sql.Connection;

/**
 * Main interface for ORM operations.
 * 
 * Similar to Hibernate's Session, this is:
 * - NOT thread-safe (use one per thread/request)
 * - Lightweight to create
 * - Contains PersistenceContext (first-level cache)
 * - Manages entity lifecycle
 */
public class MiniSession implements Closeable {

  private final Connection connection;
  private final MiniSessionFactory sessionFactory;
  private final PersistenceContext persistenceContext;
  private final EntityPersister entityPersister;
  private final SQLGenerator sqlGenerator;
  private MiniTransaction transaction;
  private boolean closed = false;

  MiniSession(Connection connection, MiniSessionFactory sessionFactory) {
    this.connection = connection;
    this.sessionFactory = sessionFactory;
    this.persistenceContext = new PersistenceContext();
    this.sqlGenerator = new SQLGenerator();
    this.entityPersister = new EntityPersister(connection, sqlGenerator);
  }

  // ==================== CRUD Operations ====================

  /**
   * Makes a transient entity persistent.
   * Entity will be INSERTed on flush/commit.
   * 
   * @param entity The transient entity to persist
   */
  public void persist(Object entity) {
    checkOpen();
    EntityMetadata metadata = getMetadata(entity.getClass());

    // Check if already managed
    Object id = metadata.getId(entity);
    if (id != null && persistenceContext.contains(entity.getClass(), id)) {
      return; // Already managed, no-op
    }

    // Mark for insertion
    persistenceContext.addEntity(entity, metadata, EntityState.MANAGED);
    persistenceContext.scheduleInsert(entity);
  }

  /**
   * Gets an entity by ID.
   * First checks cache, then queries database.
   * 
   * @param entityClass The entity type
   * @param id          The primary key
   * @return The entity or null if not found
   */
  public <T> T find(Class<T> entityClass, Object id) {
    checkOpen();
    EntityMetadata metadata = getMetadata(entityClass);

    // Check first-level cache
    Option<Object> cached = persistenceContext.getEntity(entityClass, id);
    if (cached.isDefined()) {
      return entityClass.cast(cached.get());
    }

    // Query database
    return Try.of(() -> entityPersister.load(metadata, id))
        .map(entity -> {
          if (entity != null) {
            persistenceContext.addEntity(entity, metadata, EntityState.MANAGED);
          }
          return entityClass.cast(entity);
        })
        .getOrElseThrow(e -> new RuntimeException("Failed to find entity", e));
  }

  /**
   * Removes a persistent entity.
   * Entity will be DELETEd on flush/commit.
   */
  public void remove(Object entity) {
    checkOpen();
    EntityMetadata metadata = getMetadata(entity.getClass());
    Object id = metadata.getId(entity);

    if (id == null) {
      throw new IllegalArgumentException("Cannot remove transient entity");
    }

    persistenceContext.markRemoved(entity);
    persistenceContext.scheduleDelete(entity);
  }

  /**
   * Merges a detached entity.
   * Returns the managed instance.
   */
  @SuppressWarnings("unchecked")
  public <T> T merge(T entity) {
    checkOpen();
    EntityMetadata metadata = getMetadata(entity.getClass());
    Object id = metadata.getId(entity);

    if (id == null) {
      // Transient → persist
      persist(entity);
      return entity;
    }

    // Check if already managed
    Option<Object> existing = persistenceContext.getEntity(entity.getClass(), id);
    if (existing.isDefined()) {
      // Copy state to managed entity
      copyState(entity, existing.get(), metadata);
      return (T) existing.get();
    }

    // Load from DB and copy state
    T managed = find((Class<T>) entity.getClass(), id);
    if (managed != null) {
      copyState(entity, managed, metadata);
    } else {
      persist(entity);
      managed = entity;
    }
    return managed;
  }

  // ==================== Transaction ====================

  /**
   * Begins a new transaction.
   */
  public MiniTransaction beginTransaction() {
    checkOpen();
    if (transaction != null && transaction.isActive()) {
      throw new IllegalStateException("Transaction already active");
    }
    transaction = new MiniTransaction(connection, this);
    transaction.begin();
    return transaction;
  }

  /**
   * Gets the current transaction.
   */
  public MiniTransaction getTransaction() {
    return transaction;
  }

  // ==================== Flush & Clear ====================

  /**
   * Flushes all pending changes to the database.
   * Called automatically on commit.
   */
  public void flush() {
    checkOpen();

    // Process action queues in order: INSERT → UPDATE → DELETE
    persistenceContext.getInsertQueue().forEach(entity -> {
      EntityMetadata metadata = getMetadata(entity.getClass());
      Try.run(() -> {
        Object generatedId = entityPersister.insert(metadata, entity);
        if (generatedId != null) {
          metadata.setId(entity, generatedId);
          persistenceContext.updateEntityKey(entity, metadata, generatedId);
        }
      }).getOrElseThrow(e -> new RuntimeException("Insert failed", e));
    });

    // Dirty checking and updates
    persistenceContext.detectDirtyEntities().forEach(entity -> {
      EntityMetadata metadata = getMetadata(entity.getClass());
      Try.run(() -> entityPersister.update(metadata, entity))
          .getOrElseThrow(e -> new RuntimeException("Update failed", e));
    });

    // Deletes
    persistenceContext.getDeleteQueue().forEach(entity -> {
      EntityMetadata metadata = getMetadata(entity.getClass());
      Try.run(() -> entityPersister.delete(metadata, entity))
          .getOrElseThrow(e -> new RuntimeException("Delete failed", e));
    });

    persistenceContext.clearActionQueues();
  }

  /**
   * Clears the persistence context.
   * All entities become detached.
   */
  public void clear() {
    persistenceContext.clear();
  }

  // ==================== Query ====================

  /**
   * Creates a query for the given entity type.
   */
  public <T> MiniQuery<T> createQuery(Class<T> entityClass) {
    checkOpen();
    return new MiniQuery<>(entityClass, this);
  }

  // ==================== Internal ====================

  public Connection getConnection() {
    return connection;
  }

  public PersistenceContext getPersistenceContext() {
    return persistenceContext;
  }

  public EntityMetadata getMetadata(Class<?> entityClass) {
    return sessionFactory.getEntityMetadata(entityClass);
  }

  private void copyState(Object source, Object target, EntityMetadata metadata) {
    metadata.getColumns().forEach(field -> {
      Object value = field.getValue(source);
      field.setValue(target, value);
    });
  }

  private void checkOpen() {
    if (closed) {
      throw new IllegalStateException("Session is closed");
    }
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      persistenceContext.clear();
      Try.run(connection::close);
    }
  }

  public boolean isOpen() {
    return !closed;
  }
}
