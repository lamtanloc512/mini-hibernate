package org.ltl.minihibernate.persist;

import java.util.Objects;

/**
 * Unique identifier for an entity in the persistence context.
 * Combines entity class and primary key.
 */
public final class EntityKey {

  private final Class<?> entityClass;
  private final Object id;

  public EntityKey(Class<?> entityClass, Object id) {
    this.entityClass = entityClass;
    this.id = id;
  }

  public Class<?> getEntityClass() {
    return entityClass;
  }

  public Object getId() {
    return id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    EntityKey entityKey = (EntityKey) o;
    return Objects.equals(entityClass, entityKey.entityClass)
        && Objects.equals(id, entityKey.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(entityClass, id);
  }

  @Override
  public String toString() {
    return "EntityKey{" + entityClass.getSimpleName() + "#" + id + "}";
  }
}
