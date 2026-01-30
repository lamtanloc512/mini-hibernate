package org.ltl.minihibernate.demo;

import org.ltl.minihibernate.api.MiniEntityManager;
import org.ltl.minihibernate.api.MiniEntityManagerFactory;
import org.ltl.minihibernate.config.MiniConfiguration;

import java.sql.Connection;

/**
 * Demo: Entity scanning - auto-discover @Entity classes in a package.
 */
public class EntityScanDemo {

  public static void main(String[] args) throws Exception {
    System.out.println("=== Entity Scanning Demo ===\n");

    // Scan package for @Entity classes
    MiniEntityManagerFactory factory = new MiniConfiguration()
        .setProperty(MiniConfiguration.URL, "jdbc:h2:mem:scandemo;DB_CLOSE_DELAY=-1")
        .setProperty(MiniConfiguration.USERNAME, "sa")
        .setProperty(MiniConfiguration.PASSWORD, "")
        .scanPackage("org.ltl.minihibernate.example") // Auto-scan!
        .buildSessionFactory();

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

    System.out.println("✅ Found entities in 'org.ltl.minihibernate.example' package");
    System.out.println("✅ SessionFactory built with scanned entities");

    factory.close();
    System.out.println("\n✅ Entity Scanning Demo complete!");
  }
}
