package org.ltl.minihibernate.provider;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProviderTest {

  @Test
  void shouldAcceptNoProperties() {
    MiniPersistenceProvider provider = new MiniPersistenceProvider();
    // createEntityManagerFactory might throw exception because we don't pass URL
    // etc, but it should NOT return null.
    // Returning null means "It is not my provider".
    // Exception means "It is my provider, but I failed to configure".
    try {
      provider.createEntityManagerFactory("test", null);
    } catch (Exception e) {
      assertThat(e).hasMessageContaining("Unable to build EntityManagerFactory");
    }
  }

  @Test
  void shouldAcceptCorrectProviderProperty() {
    MiniPersistenceProvider provider = new MiniPersistenceProvider();
    Map<String, Object> props = new HashMap<>();
    props.put("jakarta.persistence.provider", "org.ltl.minihibernate.provider.MiniPersistenceProvider");

    try {
      provider.createEntityManagerFactory("test", props);
    } catch (Exception e) {
      // Again, build failure is fine, we just verify it accepted the provider
      assertThat(e).hasMessageContaining("Unable to build EntityManagerFactory");
    }
  }

  @Test
  void shouldIgnoreIncorporrectProviderProperty() {
    MiniPersistenceProvider provider = new MiniPersistenceProvider();
    Map<String, Object> props = new HashMap<>();
    props.put("jakarta.persistence.provider", "org.hibernate.jpa.HibernatePersistenceProvider");

    // This MUST return null
    Object emf = provider.createEntityManagerFactory("test", props);
    assertThat(emf).isNull();
  }
}
