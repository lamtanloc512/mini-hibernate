package org.ltl.minihibernate.spring;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration
@ConditionalOnClass(org.ltl.minihibernate.provider.MiniPersistenceProvider.class)
public class MiniHibernateAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public JpaVendorAdapter jpaVendorAdapter() {
    return new MiniJpaVendorAdapter();
  }

  @Bean
  @ConditionalOnMissingBean(EntityManagerFactory.class)
  public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, JpaVendorAdapter jpaVendorAdapter, ConfigurableApplicationContext applicationContext) {
    LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
    em.setDataSource(dataSource);
    em.setJpaVendorAdapter(jpaVendorAdapter);

    List<String> packages = AutoConfigurationPackages.get(applicationContext);
    if (!packages.isEmpty()) {
      em.setPackagesToScan(packages.toArray(new String[0]));
    }
    
    return em;
  }

  @Bean
  @ConditionalOnMissingBean(PlatformTransactionManager.class)
  public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
    JpaTransactionManager transactionManager = new JpaTransactionManager();
    transactionManager.setEntityManagerFactory(emf);
    return transactionManager;
  }
}
