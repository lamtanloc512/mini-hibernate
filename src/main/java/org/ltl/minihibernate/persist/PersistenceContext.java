package org.ltl.minihibernate.persist;

import java.util.ArrayList;
import java.util.Arrays;

import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.session.EntityState;

import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;

/**
 * First-Level Cache and entity state management.
 * 
 * Implements two key patterns:
 * 1. Identity Map - ensures one instance per entity per session
 * 2. Unit of Work - tracks changes and action queues
 */
public class PersistenceContext {

  // Identity Map: (EntityClass, ID) → Entity instance
  private Map<EntityKey, Object> entityMap = HashMap.empty();

  // Snapshots for dirty checking: EntityKey → field values at load time
  private Map<EntityKey, Object[]> snapshots = HashMap.empty();

  // Entity states
  private Map<EntityKey, EntityState> states = HashMap.empty();

  // Metadata cache for entities
  private Map<EntityKey, EntityMetadata> metadataCache = HashMap.empty();

  // Action queues
  private java.util.List<Object> insertQueue = new ArrayList<>();
  private java.util.List<Object> deleteQueue = new ArrayList<>();

  // ==================== Entity Management ====================

  /**
   * Adds an entity to the persistence context.
   * Takes a snapshot for later dirty checking.
   */
  public void addEntity(Object entity, EntityMetadata metadata, EntityState state) {
    Object id = metadata.getId(entity);
    EntityKey key = new EntityKey(entity.getClass(), id);

    entityMap = entityMap.put(key, entity);
    states = states.put(key, state);
    metadataCache = metadataCache.put(key, metadata);

    // Take snapshot for dirty checking
    if (state == EntityState.MANAGED) {
      snapshots = snapshots.put(key, takeSnapshot(entity, metadata));
    }
  }

  /**
   * Updates the entity key after ID is generated.
   */
  public void updateEntityKey(Object entity, EntityMetadata metadata, Object newId) {
    // Find entity with null ID
    Option<EntityKey> oldKey = entityMap
        .filterValues(e -> e == entity)
        .keySet()
        .headOption();

    if (oldKey.isDefined()) {
      entityMap = entityMap.remove(oldKey.get());
      states = states.remove(oldKey.get());
      snapshots = snapshots.remove(oldKey.get());
      metadataCache = metadataCache.remove(oldKey.get());
    }

    EntityKey newKey = new EntityKey(entity.getClass(), newId);
    entityMap = entityMap.put(newKey, entity);
    states = states.put(newKey, EntityState.MANAGED);
    metadataCache = metadataCache.put(newKey, metadata);
    snapshots = snapshots.put(newKey, takeSnapshot(entity, metadata));
  }

  /**
   * Gets an entity from the cache by class and ID.
   */
  public Option<Object> getEntity(Class<?> entityClass, Object id) {
    return entityMap.get(new EntityKey(entityClass, id));
  }

  /**
   * Checks if an entity is in the persistence context.
   */
  public boolean contains(Class<?> entityClass, Object id) {
    return entityMap.containsKey(new EntityKey(entityClass, id));
  }

  /**
   * Marks an entity as removed.
   */
  public void markRemoved(Object entity) {
    Option<EntityKey> key = findKeyByEntity(entity);
    key.forEach(k -> states = states.put(k, EntityState.REMOVED));
  }

  // ==================== Action Queues ====================

  public void scheduleInsert(Object entity) {
    insertQueue.add(entity);
  }

  public void scheduleDelete(Object entity) {
    deleteQueue.add(entity);
  }

  public java.util.List<Object> getInsertQueue() {
    return new ArrayList<>(insertQueue);
  }

  public java.util.List<Object> getDeleteQueue() {
    return new ArrayList<>(deleteQueue);
  }

  public void clearActionQueues() {
    insertQueue.clear();
    deleteQueue.clear();
  }

  // ==================== Dirty Checking ====================

  /**
   * Detects entities that have been modified since loading.
   * Compares current field values with snapshots.
   */
  public java.util.List<Object> detectDirtyEntities() {
    java.util.List<Object> dirtyEntities = new ArrayList<>();

    for (Tuple2<EntityKey, Object> entry : entityMap) {
      EntityKey key = entry._1;
      Object entity = entry._2;

      // Skip non-managed entities and entities in insert queue
      if (states.get(key).getOrElse(EntityState.TRANSIENT) != EntityState.MANAGED) {
        continue;
      }
      if (insertQueue.contains(entity)) {
        continue; // New entities don't need UPDATE
      }

      Option<Object[]> snapshotOpt = snapshots.get(key);
      Option<EntityMetadata> metadataOpt = metadataCache.get(key);

      if (snapshotOpt.isDefined() && metadataOpt.isDefined()) {
        Object[] original = snapshotOpt.get();
        Object[] current = takeSnapshot(entity, metadataOpt.get());

        if (!Arrays.equals(original, current)) {
          dirtyEntities.add(entity);
          // Update snapshot
          snapshots = snapshots.put(key, current);
        }
      }
    }

    return dirtyEntities;
  }

  /**
   * Takes a snapshot of all field values for dirty checking.
   */
  private Object[] takeSnapshot(Object entity, EntityMetadata metadata) {
    return metadata.getAllColumns().stream()
        .map(field -> field.getValue(entity))
        .toArray();
  }

  // ==================== Utility ====================

  private Option<EntityKey> findKeyByEntity(Object entity) {
    return entityMap
        .filterValues(e -> e == entity)
        .keySet()
        .headOption();
  }

  /**
   * Clears all cached entities and queues.
   */
  public void clear() {
    entityMap = HashMap.empty();
    snapshots = HashMap.empty();
    states = HashMap.empty();
    metadataCache = HashMap.empty();
    clearActionQueues();
  }

  /**
   * Returns the number of managed entities.
   */
  public int size() {
    return entityMap.size();
  }
}
