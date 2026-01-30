package org.ltl.minihibernate.config;

import org.ltl.minihibernate.internal.MiniEntityManagerFactoryImpl;
import org.ltl.minihibernate.api.MiniEntityManagerFactory;
import org.ltl.minihibernate.util.ClasspathScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Hibernate-style configuration entry point.
 * 
 * <pre>
 * // Option 1: Default properties file
 * MiniEntityManagerFactory sf = new MiniConfiguration()
 *     .configure() // reads minihibernate.properties
 *     .buildSessionFactory();
 * 
 * // Option 2: Custom properties file
 * MiniEntityManagerFactory sf = new MiniConfiguration()
 *     .configure("custom.properties")
 *     .buildSessionFactory();
 * 
 * // Option 3: Programmatic configuration
 * MiniEntityManagerFactory sf = new MiniConfiguration()
 *     .setProperty("minihibernate.connection.url", "jdbc:mysql://...")
 *     .setProperty("minihibernate.connection.username", "root")
 *     .addAnnotatedClass(User.class)
 *     .buildSessionFactory();
 * 
 * // Option 4: Package scanning
 * MiniEntityManagerFactory sf = new MiniConfiguration()
 *     .setProperty(MiniConfiguration.ENTITY_SCAN, "org.myproject.entity")
 *     .buildSessionFactory();
 * </pre>
 */
public class MiniConfiguration {

  private static final Logger log = LoggerFactory.getLogger(MiniConfiguration.class);
  private static final String DEFAULT_PROPERTIES_FILE = "minihibernate.properties";

  // Property keys
  public static final String URL = "minihibernate.connection.url";
  public static final String USERNAME = "minihibernate.connection.username";
  public static final String PASSWORD = "minihibernate.connection.password";
  public static final String DRIVER_CLASS = "minihibernate.connection.driver_class";
  public static final String ENTITY_SCAN = "minihibernate.entity.scan";
  public static final String SHOW_SQL = "minihibernate.show_sql";
  public static final String DIALECT = "minihibernate.dialect";
  public static final String POOL_SIZE = "minihibernate.connection.pool_size";

  private final Properties properties = new Properties();
  private final List<Class<?>> annotatedClasses = new ArrayList<>();

  public MiniConfiguration() {
  }

  /**
   * Load configuration from default properties file (minihibernate.properties).
   */
  public MiniConfiguration configure() {
    return configure(DEFAULT_PROPERTIES_FILE);
  }

  /**
   * Load configuration from specified properties file.
   */
  public MiniConfiguration configure(String resource) {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
      if (is == null) {
        log.warn("Properties file '{}' not found in classpath", resource);
        return this;
      }
      properties.load(is);
      log.info("Loaded configuration from '{}'", resource);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load properties from " + resource, e);
    }
    return this;
  }

  /**
   * Set a configuration property.
   */
  public MiniConfiguration setProperty(String key, String value) {
    properties.setProperty(key, value);
    return this;
  }

  /**
   * Get a configuration property.
   */
  public String getProperty(String key) {
    return properties.getProperty(key);
  }

  /**
   * Add an annotated entity class.
   */
  public MiniConfiguration addAnnotatedClass(Class<?> entityClass) {
    annotatedClasses.add(entityClass);
    return this;
  }

  /**
   * Scan a package for @Entity annotated classes.
   */
  public MiniConfiguration scanPackage(String packageName) {
    List<Class<?>> scanned = ClasspathScanner.scan(packageName);
    annotatedClasses.addAll(scanned);
    return this;
  }

  /**
   * Build the SessionFactory (EntityManagerFactory).
   */
  public MiniEntityManagerFactory buildSessionFactory() {
    validateRequiredProperties();

    // Scan packages from property if configured
    String entityScan = properties.getProperty(ENTITY_SCAN);
    if (entityScan != null && !entityScan.isEmpty()) {
      for (String pkg : entityScan.split(",")) {
        scanPackage(pkg.trim());
      }
    }

    MiniEntityManagerFactoryImpl.Builder builder = MiniEntityManagerFactoryImpl.builder()
        .url(properties.getProperty(URL))
        .username(properties.getProperty(USERNAME))
        .password(properties.getProperty(PASSWORD, ""));

    // Add all entity classes
    for (Class<?> entityClass : annotatedClasses) {
      builder.addEntityClass(entityClass);
    }

    log.info("Building SessionFactory with {} entity classes", annotatedClasses.size());
    return builder.build();
  }

  private void validateRequiredProperties() {
    if (properties.getProperty(URL) == null) {
      throw new IllegalStateException("Missing required property: " + URL);
    }
    if (properties.getProperty(USERNAME) == null) {
      throw new IllegalStateException("Missing required property: " + USERNAME);
    }
  }
}
