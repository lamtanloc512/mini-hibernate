package com.minihibernate.demo;

import java.sql.Connection;

import com.minihibernate.api.MiniEntityManager;
import com.minihibernate.api.MiniEntityManagerFactory;
import com.minihibernate.api.MiniTransaction;
import com.minihibernate.api.MiniTypedQuery;
import com.minihibernate.example.User;
import com.minihibernate.internal.MiniEntityManagerFactoryImpl;
import com.minihibernate.internal.MiniEntityManagerImpl;

import io.vavr.control.Try;

/**
 * Demo showing how interface hides implementation.
 * 
 * This is EXACTLY how JPA/Hibernate works:
 * - You code against interfaces (MiniEntityManager)
 * - Implementation is hidden (MiniEntityManagerImpl)
 * - You can't trace directly because it's in a different class
 */
public class InterfaceImplDemo {

  public static void main(String[] args) {
    System.out.println("=== Interface vs Implementation Demo ===\n");

    // 1. Create factory - returns INTERFACE
    MiniEntityManagerFactory factory = MiniEntityManagerFactoryImpl.builder()
        .url("jdbc:mysql://localhost:3307/test_db")
        .username("root")
        .password("p@ssword")
        .addEntityClass(User.class)
        .build();

    System.out.println("Factory type (what you see):");
    System.out.println("  Interface: MiniEntityManagerFactory");
    System.out.println("  Actual:    " + factory.getClass().getName());
    System.out.println();

    Try.withResources(() -> factory.createEntityManager())
        .of(em -> {
          MiniTransaction tx = em.getTransaction();
          tx.begin();

          User user = new User();
          user.setName("Ethan");
          user.setEmail("ethan@gmail.com");
          user.setAge(33);

          em.persist(user);

          tx.commit();

          return user;
        })
        .andThen((u) -> {
          System.out.println("User: " + u);
        }).andFinallyTry(() -> factory.close());

    try (factory) {
      // Create table first
      createTable(factory);

      // 2. Create EntityManager - returns INTERFACE
      MiniEntityManager em = factory.createEntityManager();

      System.out.println("EntityManager type (what you see):");
      System.out.println(" Interface: MiniEntityManager");
      System.out.println(" Actual: " + em.getClass().getName());
      System.out.println();

      // 3. Get transaction - returns INTERFACE
      MiniTransaction tx = em.getTransaction();

      System.out.println("Transaction type (what you see):");
      System.out.println(" Interface: MiniTransaction");
      System.out.println(" Actual: " + tx.getClass().getName());
      System.out.println();

      // 4. Demonstrate unwrap() - how to get actual implementation
      System.out.println("Using unwrap() to get actual implementation:");
      MiniEntityManagerImpl actualImpl = em.unwrap(MiniEntityManagerImpl.class);
      System.out.println(" Unwrapped: " + actualImpl.getClass().getName());

      Connection conn = em.unwrap(Connection.class);
      System.out.println(" Connection: " + conn.getClass().getName());
      System.out.println();

      // 5. Normal usage - only see interface methods
      tx.begin();

      User user = new User();
      user.setName("JPA Style User");
      user.setEmail("jpa@example.com");

      // When you debug persist(), you only see MiniEntityManager interface
      // But actual code runs in MiniEntityManagerImpl.persist()
      em.persist(user);

      tx.commit();
      System.out.println("Persisted user with ID: " + user.getId());

      // 6. Query - returns INTERFACE
      MiniTypedQuery<User> query = em.createQuery(User.class);
      System.out.println("\nQuery type (what you see):");
      System.out.println(" Interface: MiniTypedQuery");
      System.out.println(" Actual: " + query.getClass().getName());

      em.close();
    } catch (Exception e) {
      e.printStackTrace();
    }

    System.out.println("\n=== Summary ===");
    System.out.println("You code against: com.minihibernate.api.* (interfaces)");
    System.out.println("Code executes in: com.minihibernate.internal.*(implementations)");
    System.out.println("\nThis is why you can't trace into implementationeasily!");
  }

  private static void createTable(MiniEntityManagerFactory factory) {
    try (MiniEntityManager em = factory.createEntityManager()) {
      Connection conn = em.unwrap(Connection.class);
      conn.createStatement().execute("DROP TABLE IF EXISTS users");
      conn.createStatement().execute("""
              CREATE TABLE users (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  name VARCHAR(255),
                  email VARCHAR(255),
                  age INT
              )
          """);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create table", e);
    }
  }
}
