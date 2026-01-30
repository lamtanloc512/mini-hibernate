package org.ltl.minihibernate.internal;

import java.util.Properties;

import org.ltl.minihibernate.api.MiniEntityManager;
import org.ltl.minihibernate.api.MiniEntityManagerFactory;
import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.metadata.MetadataParser;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import javax.sql.DataSource;
import io.vavr.control.Try;
import jakarta.persistence.Cache;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitTransactionType;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.Query;
import jakarta.persistence.SchemaManager;
import jakarta.persistence.SynchronizationType;
import jakarta.persistence.TypedQueryReference;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.metamodel.Metamodel;

public class MiniEntityManagerFactoryImpl implements MiniEntityManagerFactory {

  private final DataSource dataSource;
  private final MetadataParser metadataParser;
  private final Map<Class<?>, EntityMetadata> entityMetadataMap;
  private final Map<String, Object> properties;

  private boolean open = true;

  private MiniEntityManagerFactoryImpl(Builder builder) {
    this.properties = HashMap.ofAll(builder.properties).mapKeys(String::valueOf).mapValues(v -> (Object) v);
    if (builder.dataSource != null) {
      this.dataSource = builder.dataSource;
    } else {
      this.dataSource = createDataSource(builder.properties);
    }
    this.metadataParser = new MetadataParser();
    this.entityMetadataMap = parseEntities(builder.entityClasses);
  }

  // Default constructor for testing/safe init
  public MiniEntityManagerFactoryImpl() {
    this(new Builder());
  }

  @Override
  public MiniEntityManager createEntityManager() {
    verifyOpen();
    return Try.of(() -> dataSource.getConnection())
        .map(conn -> (MiniEntityManager) new MiniEntityManagerImpl(conn, this))
        .getOrElseThrow(e -> new RuntimeException("Failed to create EntityManager", e));
  }

  @Override
  public EntityManager createEntityManager(java.util.Map<?, ?> map) {
    return createEntityManager(); // Proper impl would merge properties
  }

  @Override
  public EntityManager createEntityManager(SynchronizationType type) {
    return createEntityManager();
  }

  @Override
  public EntityManager createEntityManager(SynchronizationType type, java.util.Map<?, ?> map) {
    return createEntityManager();
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public synchronized void close() {
    if (open) {
      open = false;
      if (dataSource instanceof HikariDataSource) {
        HikariDataSource hds = (HikariDataSource) dataSource;
        if (!hds.isClosed()) {
           hds.close();
        }
      }
    }
  }

  // --- Internal Accessors ---

  public EntityMetadata getEntityMetadata(Class<?> entityClass) {
    return entityMetadataMap.get(entityClass)
        .getOrElseThrow(() -> new IllegalArgumentException("Unknown entity: " + entityClass.getName()));
  }

  public DataSource getDataSource() {
    return dataSource;
  }

  // --- Helpers ---

  private void verifyOpen() {
    if (!open) {
      throw new IllegalStateException("EntityManagerFactory is closed");
    }
  }

  private HikariDataSource createDataSource(Properties props) {
    HikariConfig config = new HikariConfig();

    // Core JDBC
    config.setJdbcUrl(props.getProperty("jakarta.persistence.jdbc.url",
        props.getProperty("javax.persistence.jdbc.url",
            props.getProperty("url", "jdbc:h2:mem:test"))));

    config.setUsername(props.getProperty("jakarta.persistence.jdbc.user",
        props.getProperty("javax.persistence.jdbc.user",
            props.getProperty("username", "sa"))));

    config.setPassword(props.getProperty("jakarta.persistence.jdbc.password",
        props.getProperty("javax.persistence.jdbc.password",
            props.getProperty("password", ""))));

    // Pool settings
    config.setMaximumPoolSize(Integer.parseInt(props.getProperty("pool.size", "10")));

    return new HikariDataSource(config);
  }

  private Map<Class<?>, EntityMetadata> parseEntities(java.util.Set<Class<?>> classes) {
    return io.vavr.collection.List.ofAll(classes)
        .toMap(clazz -> clazz, metadataParser::parse);
  }

  // --- Builder ---

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final Properties properties = new Properties();
    private final java.util.Set<Class<?>> entityClasses = new java.util.HashSet<>();
    private DataSource dataSource;

    public Builder dataSource(DataSource dataSource) {
      this.dataSource = dataSource;
      return this;
    }

    public Builder url(String url) {
      properties.setProperty("jakarta.persistence.jdbc.url", url);
      return this;
    }

    public Builder username(String username) {
      properties.setProperty("jakarta.persistence.jdbc.user", username);
      return this;
    }

    public Builder password(String password) {
      properties.setProperty("jakarta.persistence.jdbc.password", password);
      return this;
    }

    public Builder property(String key, String value) {
      properties.setProperty(key, value);
      return this;
    }

    public Builder addEntityClass(Class<?> entityClass) {
      entityClasses.add(entityClass);
      return this;
    }

    public MiniEntityManagerFactory build() {
      return new MiniEntityManagerFactoryImpl(this);
    }
  }

  // --- JPA API Stubs ---

  @Override
  public java.util.Map<String, Object> getProperties() {
    return properties.toJavaMap();
  }

  @Override
  public PersistenceUnitUtil getPersistenceUnitUtil() {
    return (PersistenceUnitUtil) java.lang.reflect.Proxy.newProxyInstance(
        PersistenceUnitUtil.class.getClassLoader(),
        new Class<?>[] { PersistenceUnitUtil.class },
        (proxy, method, args) -> {
          if (method.getName().equals("getIdentifier")) {
            return args[0] == null ? null
                : entityMetadataMap.get(args[0].getClass())
                    .map(m -> m.getId(args[0]))
                    .getOrNull();
          }
          return method.getName().equals("isLoaded");
        });
  }

  @Override
  public CriteriaBuilder getCriteriaBuilder() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Metamodel getMetamodel() {
    return new MiniMetamodelImpl(entityMetadataMap);
  }

  @Override
  public Cache getCache() {
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
  public <E> java.util.Map<String, EntityGraph<? extends E>> getNamedEntityGraphs(Class<E> entityType) {
    return java.util.Collections.emptyMap();
  }

  @Override
  public <R> R callInTransaction(java.util.function.Function<EntityManager, R> function) {
    EntityManager em = createEntityManager();
    try {
      em.getTransaction().begin();
      R result = function.apply(em);
      em.getTransaction().commit();
      return result;
    } catch (Exception e) {
      if (em.getTransaction().isActive())
        em.getTransaction().rollback();
      throw e;
    } finally {
      em.close();
    }
  }

  @Override
  public void runInTransaction(java.util.function.Consumer<EntityManager> consumer) {
    callInTransaction(em -> {
      consumer.accept(em);
      return null;
    });
  }

  @Override
  public void addNamedQuery(String name, Query query) {
    throw new UnsupportedOperationException();
  }
}
