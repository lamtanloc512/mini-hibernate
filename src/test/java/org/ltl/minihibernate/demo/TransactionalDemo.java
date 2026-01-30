package org.ltl.minihibernate.demo;

import org.ltl.minihibernate.api.MiniEntityManager;
import org.ltl.minihibernate.api.MiniEntityManagerFactory;
import org.ltl.minihibernate.config.MiniConfiguration;
import org.ltl.minihibernate.example.User;
import org.ltl.minihibernate.transaction.TransactionInterceptor;
import org.ltl.minihibernate.transaction.Transactional;

import java.sql.Connection;

/**
 * Demo: @Transactional annotation with automatic transaction management.
 */
public class TransactionalDemo {

  public static void main(String[] args) throws Exception {
    System.out.println("=== @Transactional Demo ===\n");

    // Setup
    MiniEntityManagerFactory factory = new MiniConfiguration()
        .setProperty(MiniConfiguration.URL, "jdbc:h2:mem:txdemo;DB_CLOSE_DELAY=-1")
        .setProperty(MiniConfiguration.USERNAME, "sa")
        .setProperty(MiniConfiguration.PASSWORD, "")
        .addAnnotatedClass(User.class)
        .buildSessionFactory();

    // Create table
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

    // Create transactional proxy
    UserService service = TransactionInterceptor.createProxy(
        new UserServiceImpl(), 
        UserService.class, 
        factory
    );

    // Test successful transaction
    System.out.println("1. Testing successful transaction...");
    service.createUser("John", "john@example.com");
    System.out.println("   ✅ User created with auto-commit\n");

    // Test rollback on exception
    System.out.println("2. Testing rollback on exception...");
    try {
      service.createUserWithError("Jane", "jane@example.com");
    } catch (RuntimeException e) {
      System.out.println("   ✅ Exception caught, transaction rolled back: " + e.getMessage());
    }

    factory.close();
    System.out.println("\n✅ @Transactional Demo complete!");
  }

  // Service interface
  public interface UserService {
    void createUser(String name, String email);
    void createUserWithError(String name, String email);
  }

  // Service implementation with @Transactional
  public static class UserServiceImpl implements UserService {
    
    @Override
    @Transactional
    public void createUser(String name, String email) {
      MiniEntityManager em = TransactionInterceptor.getCurrentEntityManager();
      User user = new User(name, email);
      em.persist(user);
      System.out.println("   Created: " + user);
    }

    @Override
    @Transactional
    public void createUserWithError(String name, String email) {
      MiniEntityManager em = TransactionInterceptor.getCurrentEntityManager();
      User user = new User(name, email);
      em.persist(user);
      
      // Simulate error - transaction should rollback
      throw new RuntimeException("Simulated error - should rollback");
    }
  }
}
