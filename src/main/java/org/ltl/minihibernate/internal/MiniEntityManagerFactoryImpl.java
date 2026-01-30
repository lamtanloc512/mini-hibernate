package org.ltl.minihibernate.internal;

import java.util.Properties;

import javax.management.Query;

import org.ltl.minihibernate.api.MiniEntityManager;
import org.ltl.minihibernate.api.MiniEntityManagerFactory;
import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.metadata.MetadataParser;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Try;
import jakarta.persistence.Cache;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitTransactionType;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.SchemaManager;
import jakarta.persistence.SynchronizationType;
import jakarta.persistence.TypedQueryReference;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.metamodel.Metamodel;

/**
 * MiniEntityManagerFactoryImpl - The factory implementation.
 * 
 * Like Hibernate's SessionFactoryImpl.
 * 
 * Users call: factory.createEntityManager() → returns MiniEntityManager
 * interface
 * Actual object is: MiniEntityManagerImpl (hidden)
 */
public class MiniEntityManagerFactoryImpl implements MiniEntityManagerFactory {

  private final HikariDataSource dataSource;
  private final MetadataParser metadataParser;
  private final Map<Class<?>, EntityMetadata> entityMetadataMap;
  private boolean open = true;

  private MiniEntityManagerFactoryImpl(Builder builder) {
    this.dataSource = createDataSource(builder.properties);
    this.metadataParser = new MetadataParser();
    this.entityMetadataMap = parseEntities(builder.entityClasses);
  }

  @Override
  public MiniEntityManager createEntityManager() {
    if (!open) {
      throw new IllegalStateException("Factory is closed");
    }
    // Returns the INTERFACE, but creates the IMPLEMENTATION
    return Try.of(() -> dataSource.getConnection())
        .map(conn -> (MiniEntityManager) new MiniEntityManagerImpl(conn, this))
        .getOrElseThrow(e -> new RuntimeException("Failed to create EntityManager", e));
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void close() {
    if (open) {
      open = false;
      if (dataSource != null && !dataSource.isClosed()) {
        dataSource.close();
      }
    }
  }

  // Package-private: only internal classes can access
  EntityMetadata getEntityMetadata(Class<?> entityClass) {
    return entityMetadataMap.get(entityClass)
        .getOrElseThrow(() -> new IllegalArgumentException(
            "Unknown entity: " + entityClass.getName()));
  }

  private HikariDataSource createDataSource(Properties props) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(props.getProperty("url", "jdbc:h2:mem:test"));
    config.setUsername(props.getProperty("username", "sa"));
    config.setPassword(props.getProperty("password", ""));
    config.setMaximumPoolSize(Integer.parseInt(props.getProperty("pool.size", "10")));
    return new HikariDataSource(config);
  }

  private Map<Class<?>, EntityMetadata> parseEntities(java.util.Set<Class<?>> classes) {
    Map<Class<?>, EntityMetadata> result = HashMap.empty();
    for (Class<?> clazz : classes) {
      EntityMetadata metadata = metadataParser.parse(clazz);
      result = result.put(clazz, metadata);
    }
    return result;
  }

  // ==================== Builder ====================

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final Properties properties = new Properties();
    private final java.util.Set<Class<?>> entityClasses = new java.util.HashSet<>();

    public Builder url(String url) {
      properties.setProperty("url", url);
      return this;
    }

    public Builder username(String username) {
      properties.setProperty("username", username);
      return this;
    }

    public Builder password(String password) {
      properties.setProperty("password", password);
      return this;
    }

    public Builder addEntityClass(Class<?> entityClass) {
      entityClasses.add(entityClass);
      return this;
    }

    public MiniEntityManagerFactory build() {
      // Returns INTERFACE, creates IMPLEMENTATION
      return new MiniEntityManagerFactoryImpl(this);
    }
  }

  // Default constructor for PersistenceProvider
  public MiniEntityManagerFactoryImpl() {
    this.dataSource = createDataSource(new Properties());
    this.metadataParser = new MetadataParser();
    this.entityMetadataMap = HashMap.empty();
  }

  @Override
  public EntityManager createEntityManager(java.util.Map<?, ?> map) {
    return createEntityManager();
  }

  @Override
  public EntityManager createEntityManager(SynchronizationType type) {
    return createEntityManager();
  }

  @Override
  public EntityManager createEntityManager(SynchronizationType type,
      java.util.Map<?, ?> map) {
    return createEntityManager();
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
  public java.util.Map<String, Object> getProperties() {
    return java.util.Collections.emptyMap();
  }

  @Override
  public Cache getCache() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PersistenceUnitUtil getPersistenceUnitUtil() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T unwrap(Class<T> cls) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> void addNamedEntityGraph(String graphName, EntityGraph<T> entityGraph) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getName() {
    return "mini-hibernate";
  }

  @Override
  public PersistenceUnitTransactionType getTransactionType() {
    return PersistenceUnitTransactionType.RESOURCE_LOCAL;
  }

  @Override
  public SchemaManager getSchemaManager() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <R> java.util.Map<String, TypedQueryReference<R>> getNamedQueries(Class<R> resultType) {
    return java.util.Collections.emptyMap();
  }

  @Override
  public <E> java.util.Map<String, EntityGraph<? extends E>> getNamedEntityGraphs(
      Class<E> entityType) {
    return java.util.Collections.emptyMap();
  }

  @Override
  public <R> R callInTransaction(java.util.function.Function<EntityManager, R> function) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void runInTransaction(java.util.function.Consumer<EntityManager> consumer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addNamedQuery(String name, jakarta.persistence.Query query) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'addNamedQuery'");
  }

  @Override
  public void addNamedQuery(String name, Query query) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'addNamedQuery'");
  }
}
