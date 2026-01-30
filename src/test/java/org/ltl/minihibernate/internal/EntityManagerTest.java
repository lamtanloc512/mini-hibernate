package org.ltl.minihibernate.internal;

import org.junit.jupiter.api.Test;
import org.ltl.minihibernate.api.MiniEntityManager;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class EntityManagerTest {

  @Test
  void shouldThrowExceptionWhenClosed() {
    // Setup
    MiniEntityManagerFactoryImpl factory = new MiniEntityManagerFactoryImpl(); // uses default H2 config
    MiniEntityManager em = factory.createEntityManager();

    // Sanity check
    assertThat(em.isOpen()).isTrue();

    // Close
    em.close();
    assertThat(em.isOpen()).isFalse();

    // Verify operations throw IllegalStateException
    assertThatThrownBy(() -> em.persist(new Object()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("closed");

    assertThatThrownBy(() -> em.find(Object.class, 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("closed");

    assertThatThrownBy(() -> em.flush())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("closed");

    // Close again should be no-op
    em.close();

    factory.close();
  }

  @Test
  void factoryShouldAlsoCloseDataSource() {
    MiniEntityManagerFactoryImpl factory = new MiniEntityManagerFactoryImpl();
    assertThat(factory.isOpen()).isTrue();

    factory.close();
    assertThat(factory.isOpen()).isFalse();

    assertThatThrownBy(factory::createEntityManager)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("closed");
  }
}
