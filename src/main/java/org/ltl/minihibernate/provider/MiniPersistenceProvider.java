package org.ltl.minihibernate.provider;

import java.util.Map;
import java.util.Properties;

import org.ltl.minihibernate.internal.MiniEntityManagerFactoryImpl;

import io.vavr.control.Option;
import io.vavr.control.Try;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceConfiguration;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.spi.LoadState;
import jakarta.persistence.spi.PersistenceProvider;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.ProviderUtil;

public class MiniPersistenceProvider implements PersistenceProvider, ProviderUtil {

  private static final String PROVIDER_PROP = "jakarta.persistence.provider";
  private static final String OLD_PROVIDER_PROP = "javax.persistence.provider";

  @Override
  public EntityManagerFactory createEntityManagerFactory(String emName, Map<?, ?> properties) {
    Map<?, ?> nonNullProps = Option.of(properties).getOrElse(java.util.Collections.emptyMap());

    // Check if this provider is the one targeted (EclipseLink pattern)
    if (!checkForProviderProperty(nonNullProps)) {
      return null;
    }

    String unitName = Option.of(emName).getOrElse("");

    // In a real scenario, we would look up the PersistenceUnitInfo by name here.
    // For this mini-implementation, we'll create a factory directly if possible,
    // or assume we are bootstrapping in a simple SE environment where the name
    // matters less
    // or we just build with what we have.
    // ACTUALLY: The standard way is to read persistence.xml.
    // Since we are "mini", let's keep it simple: if properties are passed, use
    // them.

    return createFactory(unitName, nonNullProps);
  }

  @Override
  public EntityManagerFactory createContainerEntityManagerFactory(PersistenceUnitInfo info, Map<?, ?> properties) {
    Map<?, ?> nonNullProps = Option.of(properties).getOrElse(java.util.Collections.emptyMap());

    return createFactory(info, nonNullProps);
  }

  @Override
  public EntityManagerFactory createEntityManagerFactory(PersistenceConfiguration configuration) {
    if (!isProviderTargeted(configuration)) {
      return null;
    }

    // Basic support for JPA 3.2 bootstrapping
    // In a full implementation, we'd find the root URL using StackWalker as seen in
    // code.md
    return createFactory(configuration.name(), configuration.properties());
  }

  @Override
  public void generateSchema(PersistenceUnitInfo info, Map<?, ?> properties) {
    if (checkForProviderProperty(Option.of(properties).getOrElse(java.util.Collections.emptyMap()))) {
      // Generate DDL by creating a factory (which triggers schema gen if configured)
      // and closing it
      createContainerEntityManagerFactory(info, properties).close();
    }
  }

  @Override
  public boolean generateSchema(String persistenceUnitName, Map<?, ?> properties) {
    Map<?, ?> nonNullProps = Option.of(properties).getOrElse(java.util.Collections.emptyMap());
    if (checkForProviderProperty(nonNullProps)) {
      // Create and close to trigger DDL
      EntityManagerFactory emf = createEntityManagerFactory(persistenceUnitName, properties);
      if (emf != null) {
        emf.close();
        return true;
      }
    }
    return false;
  }

  @Override
  public ProviderUtil getProviderUtil() {
    return this;
  }

  // --- ProviderUtil Implementation ---

  @Override
  public LoadState isLoadedWithoutReference(Object entity, String attributeName) {
    return LoadState.UNKNOWN; // Naive implementation
  }

  @Override
  public LoadState isLoadedWithReference(Object entity, String attributeName) {
    return LoadState.UNKNOWN; // Naive implementation
  }

  @Override
  public LoadState isLoaded(Object entity) {
    return LoadState.UNKNOWN; // Naive implementation
  }

  // --- Internal Helpers ---

  private EntityManagerFactory createFactory(String unitName, Map<?, ?> props) {
    MiniEntityManagerFactoryImpl.Builder builder = MiniEntityManagerFactoryImpl.builder();
    applyProperties(builder, props);
    try {
      return builder.build();
    } catch (Exception e) {
      throw new PersistenceException("Unable to build EntityManagerFactory for " + unitName, e);
    }
  }

  private EntityManagerFactory createFactory(PersistenceUnitInfo info, Map<?, ?> props) {
    MiniEntityManagerFactoryImpl.Builder builder = MiniEntityManagerFactoryImpl.builder();

    // Load managed classes using the correct ClassLoader
    ClassLoader cl = Option.of(info.getClassLoader())
        .getOrElse(Thread.currentThread().getContextClassLoader());

    io.vavr.collection.List.ofAll(info.getManagedClassNames())
        .forEach(className -> Try.run(() -> builder.addEntityClass(Class.forName(className, true, cl)))
            .onFailure(e -> System.err.println("Failed to load class: " + className)));

    // Apply properties
    Option.of(info.getProperties()).peek(p -> applyProperties(builder, p));
    applyProperties(builder, props);

    return builder.build();
  }

  private void applyProperties(MiniEntityManagerFactoryImpl.Builder builder, Map<?, ?> props) {
    getVal(props, "jakarta.persistence.jdbc.url", "javax.persistence.jdbc.url", "url")
        .peek(builder::url);

    getVal(props, "jakarta.persistence.jdbc.user", "javax.persistence.jdbc.user", "username")
        .peek(builder::username);

    getVal(props, "jakarta.persistence.jdbc.password", "javax.persistence.jdbc.password", "password")
        .peek(builder::password);
  }

  private void applyProperties(MiniEntityManagerFactoryImpl.Builder builder, Properties props) {
    Map<?, ?> map = (Map<?, ?>) props; // Cast safely for read-only access or use dedicated method
    // Actually Properties implements Map<Object,Object>, so we can just reuse the
    // Map method
    applyProperties(builder, (Map<?, ?>) map);
  }

  private Option<String> getVal(Map<?, ?> props, String... keys) {
    return io.vavr.collection.List.of(keys)
        .map(key -> props.get(key))
        .filter(val -> val != null)
        .map(Object::toString)
        .headOption();
  }

  /**
   * Check recursively if the provider property points to us.
   */
  private boolean checkForProviderProperty(Map<?, ?> properties) {
    Object prop = properties.get(PROVIDER_PROP);
    if (prop == null) {
      prop = properties.get(OLD_PROVIDER_PROP);
    }

    if (prop == null) {
      return true; // No provider specified, so we are a candidate
    }

    return isProviderPropertyMini(prop);
  }

  private boolean isProviderTargeted(PersistenceConfiguration config) {
    // Property takes precedence
    if (config.properties().containsKey(PROVIDER_PROP)) {
      return isProviderPropertyMini(config.properties().get(PROVIDER_PROP));
    }
    if (config.provider() != null && !config.provider().isEmpty()) {
      return checkMiniProviderClassName(config.provider());
    }
    return true;
  }

  private boolean isProviderPropertyMini(Object providerProperty) {
    String providerClassName = null;
    if (providerProperty instanceof String) {
      providerClassName = (String) providerProperty;
    } else if (providerProperty instanceof Class) {
      providerClassName = ((Class<?>) providerProperty).getName();
    }

    return providerClassName != null && checkMiniProviderClassName(providerClassName);
  }

  private boolean checkMiniProviderClassName(String providerClassName) {
    return MiniPersistenceProvider.class.getName().equals(providerClassName);
  }
}
