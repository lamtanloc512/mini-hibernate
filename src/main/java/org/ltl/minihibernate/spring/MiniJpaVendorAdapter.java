package org.ltl.minihibernate.spring;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.spi.PersistenceProvider;
import org.ltl.minihibernate.provider.MiniPersistenceProvider;
import org.springframework.orm.jpa.vendor.AbstractJpaVendorAdapter;

public class MiniJpaVendorAdapter extends AbstractJpaVendorAdapter {

  private final PersistenceProvider persistenceProvider = new MiniPersistenceProvider();

  @SuppressWarnings("null")
  @Override
  public PersistenceProvider getPersistenceProvider() {
    return this.persistenceProvider;
  }

  @Override
  public String getPersistenceProviderRootPackage() {
    return "org.ltl.minihibernate";
  }

  @SuppressWarnings("null")
  @Override
  public Class<? extends EntityManagerFactory> getEntityManagerFactoryInterface() {
    return EntityManagerFactory.class;
  }

  @SuppressWarnings("null")
  @Override
  public Class<? extends EntityManager> getEntityManagerInterface() {
    return EntityManager.class;
  }
}
