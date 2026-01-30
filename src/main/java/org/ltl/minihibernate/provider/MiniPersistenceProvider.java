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
    // In a real implementation, we would parse persistence.xml
    // For mini-hibernate, we'll return a basic implementation if the name matches
    if ("mini-hibernate".equals(emName)) {
      // This would normally need a DataSource or connection details from the map
      // For now, we assume MiniEntityManagerFactoryImpl can handle it or has defaults
      return new MiniEntityManagerFactoryImpl();
    }
    return null;
  }

  @Override
  public EntityManagerFactory createContainerEntityManagerFactory(PersistenceUnitInfo info,
      Map<?, ?> properties) {
    return new MiniEntityManagerFactoryImpl();
  }

  @Override
  public void generateSchema(PersistenceUnitInfo info, Map<?, ?> properties) {
    // Not implemented
  }

  @Override
  public boolean generateSchema(String persistenceUnitName, Map<?, ?> properties) {
    return false;
  }

  @Override
  public ProviderUtil getProviderUtil() {
    return null; // Simplified
  }

  @Override
  public EntityManagerFactory createEntityManagerFactory(PersistenceConfiguration configuration) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'createEntityManagerFactory'");
  }
}
