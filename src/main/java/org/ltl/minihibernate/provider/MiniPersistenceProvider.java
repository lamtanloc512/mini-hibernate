package org.ltl.minihibernate.provider;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceConfiguration;
import jakarta.persistence.spi.PersistenceProvider;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.ProviderUtil;
import org.ltl.minihibernate.internal.MiniEntityManagerFactoryImpl;

import java.util.Map;

public class MiniPersistenceProvider implements PersistenceProvider {

  @Override
  public EntityManagerFactory createEntityManagerFactory(String emName, Map<?, ?> properties) {
    if (emName != null && !emName.equals("mini-hibernate")) {
      return null;
    }

    MiniEntityManagerFactoryImpl.Builder builder = MiniEntityManagerFactoryImpl.builder();
    if (properties != null) {
      if (properties.containsKey("jakarta.persistence.jdbc.url")) {
        builder.url(properties.get("jakarta.persistence.jdbc.url").toString());
      }
      if (properties.containsKey("jakarta.persistence.jdbc.user")) {
        builder.username(properties.get("jakarta.persistence.jdbc.user").toString());
      }
      if (properties.containsKey("jakarta.persistence.jdbc.password")) {
        builder.password(properties.get("jakarta.persistence.jdbc.password").toString());
      }
    }

    return builder.build();
  }

  @Override
  public EntityManagerFactory createContainerEntityManagerFactory(PersistenceUnitInfo info,
      Map<?, ?> properties) {
    MiniEntityManagerFactoryImpl.Builder builder = MiniEntityManagerFactoryImpl.builder();

    // In a real container, we'd use info.getNonJtaDataSource()
    // For mini-hibernate, we'll extract from info's properties if present
    if (info.getProperties() != null) {
      String url = info.getProperties().getProperty("jakarta.persistence.jdbc.url");
      if (url != null)
        builder.url(url);
    }

    return builder.build();
  }

  @Override
  public void generateSchema(PersistenceUnitInfo info, Map<?, ?> properties) {
    // Schema generation not yet supported
  }

  @Override
  public boolean generateSchema(String persistenceUnitName, Map<?, ?> properties) {
    return false;
  }

  @Override
  public ProviderUtil getProviderUtil() {
    return new ProviderUtil() {
      @Override
      public jakarta.persistence.spi.LoadState isLoadedWithoutReference(Object entity, String attributeName) {
        return jakarta.persistence.spi.LoadState.UNKNOWN;
      }

      @Override
      public jakarta.persistence.spi.LoadState isLoadedWithReference(Object entity, String attributeName) {
        return jakarta.persistence.spi.LoadState.UNKNOWN;
      }

      @Override
      public jakarta.persistence.spi.LoadState isLoaded(Object entity) {
        return jakarta.persistence.spi.LoadState.UNKNOWN;
      }
    };
  }

  @Override
  public EntityManagerFactory createEntityManagerFactory(PersistenceConfiguration configuration) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'createEntityManagerFactory'");
  }
}
