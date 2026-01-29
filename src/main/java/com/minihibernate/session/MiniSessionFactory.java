package com.minihibernate.session;

import com.minihibernate.metadata.EntityMetadata;
import com.minihibernate.metadata.MetadataParser;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Try;

import java.io.Closeable;
import java.util.Properties;

/**
 * Factory for creating MiniSession instances.
 * 
 * Similar to Hibernate's SessionFactory, this is:
 * - Thread-safe and immutable after creation
 * - Expensive to create (parse entities, setup connection pool)
 * - Creates lightweight Session instances
 */
public class MiniSessionFactory implements Closeable {

  private final HikariDataSource dataSource;
  private final MetadataParser metadataParser;
  private final Map<Class<?>, EntityMetadata> entityMetadataMap;

  private MiniSessionFactory(Builder builder) {
    this.dataSource = createDataSource(builder.properties);
    this.metadataParser = new MetadataParser();
    this.entityMetadataMap = parseEntities(builder.entityClasses);
  }

  /**
   * Opens a new session for database operations.
   * Each session has its own PersistenceContext (first-level cache).
   */
  public MiniSession openSession() {
    return Try.of(() -> dataSource.getConnection())
        .map(conn -> new MiniSession(conn, this))
        .getOrElseThrow(e -> new RuntimeException("Failed to open session", e));
  }

  /**
   * Gets metadata for an entity class.
   */
  public EntityMetadata getEntityMetadata(Class<?> entityClass) {
    return entityMetadataMap.get(entityClass)
        .getOrElseThrow(() -> new IllegalArgumentException(
            "Unknown entity: " + entityClass.getName()));
  }

  /**
   * Checks if a class is a registered entity.
   */
  public boolean isEntity(Class<?> clazz) {
    return entityMetadataMap.containsKey(clazz);
  }

  @Override
  public void close() {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
    }
  }

  private HikariDataSource createDataSource(Properties props) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(props.getProperty("mini.hibernate.url", "jdbc:h2:mem:test"));
    config.setUsername(props.getProperty("mini.hibernate.username", "sa"));
    config.setPassword(props.getProperty("mini.hibernate.password", ""));
    config.setMaximumPoolSize(Integer.parseInt(
        props.getProperty("mini.hibernate.pool.size", "10")));
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
      properties.setProperty("mini.hibernate.url", url);
      return this;
    }

    public Builder username(String username) {
      properties.setProperty("mini.hibernate.username", username);
      return this;
    }

    public Builder password(String password) {
      properties.setProperty("mini.hibernate.password", password);
      return this;
    }

    public Builder poolSize(int size) {
      properties.setProperty("mini.hibernate.pool.size", String.valueOf(size));
      return this;
    }

    public Builder addEntityClass(Class<?> entityClass) {
      entityClasses.add(entityClass);
      return this;
    }

    public Builder addEntityClasses(Class<?>... classes) {
      java.util.Collections.addAll(entityClasses, classes);
      return this;
    }

    public MiniSessionFactory build() {
      return new MiniSessionFactory(this);
    }
  }
}
