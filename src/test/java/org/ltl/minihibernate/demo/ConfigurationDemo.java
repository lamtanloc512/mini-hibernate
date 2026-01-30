package org.ltl.minihibernate.demo;

import org.ltl.minihibernate.api.MiniEntityManager;
import org.ltl.minihibernate.api.MiniEntityManagerFactory;
import org.ltl.minihibernate.config.MiniConfiguration;
import org.ltl.minihibernate.example.User;

import java.sql.Connection;

/**
 * Demo: Hibernate-style configuration using MiniConfiguration.
 * 
 * Supports 3 configuration styles:
 * 1. Properties file (minihibernate.properties)
 * 2. Custom properties file
 * 3. Programmatic configuration
 */
public class ConfigurationDemo {

  public static void main(String[] args) throws Exception {
    System.out.println("=== MiniConfiguration Demo ===\n");

    // Option 1: Programmatic configuration (most common for testing)
    MiniEntityManagerFactory factory = new MiniConfiguration()
        .setProperty(MiniConfiguration.URL, "jdbc:h2:mem:configdemo;DB_CLOSE_DELAY=-1")
        .setProperty(MiniConfiguration.USERNAME, "sa")
        .setProperty(MiniConfiguration.PASSWORD, "")
        .addAnnotatedClass(User.class)
        .buildSessionFactory();

    System.out.println("✅ SessionFactory created via programmatic configuration");

    // Setup test table
    try (MiniEntityManager em = factory.createEntityManager()) {
      Connection conn = em.unwrap(Connection.class);
      conn.createStatement().execute("""
          CREATE TABLE IF NOT EXISTS users (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              name VARCHAR(255),
              email VARCHAR(255),
              age INT
          )
          """);
    }

    // Demo CRUD
    try (MiniEntityManager em = factory.createEntityManager()) {
      em.getTransaction().begin();

      User user = new User("John", "john@example.com");
      user.setAge(30);
      em.persist(user);

      em.getTransaction().commit();
      System.out.println("✅ Saved: " + user);
    }

    // Option 2: Properties file (uncomment to try)
    // MiniEntityManagerFactory factory2 = new MiniConfiguration()
    // .configure() // reads minihibernate.properties
    // .buildSessionFactory();

    // Option 3: Custom properties file
    // MiniEntityManagerFactory factory3 = new MiniConfiguration()
    // .configure("custom-db.properties")
    // .buildSessionFactory();

    factory.close();
    System.out.println("\n✅ Demo complete!");
  }
}
