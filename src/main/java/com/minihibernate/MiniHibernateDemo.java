package com.minihibernate;

import com.minihibernate.example.User;
import com.minihibernate.session.MiniSession;
import com.minihibernate.session.MiniSessionFactory;
import com.minihibernate.session.MiniTransaction;

/**
 * Demo showing how to use Mini-Hibernate.
 * 
 * This demonstrates the core ORM workflow:
 * 1. Create SessionFactory
 * 2. Open Session
 * 3. Begin Transaction
 * 4. Perform CRUD operations
 * 5. Commit Transaction
 * 6. Close Session
 */
public class MiniHibernateDemo {

  public static void main(String[] args) {
    // 1. Build SessionFactory (expensive, do once per app)
    try (MiniSessionFactory sessionFactory = MiniSessionFactory.builder()
        .url("jdbc:mysql://localhost:3307/test_db")
        .username("root")
        .password("p@ssword")
        .addEntityClass(User.class)
        .build()) {

      // Create table (in real app, use schema migration)
      createTable(sessionFactory);

      // 2. Open Session (cheap, one per request/thread)
      try (MiniSession session = sessionFactory.openSession()) {

        // 3. Begin Transaction
        MiniTransaction tx = session.beginTransaction();

        try {
          // 4. Create and persist entity
          User user = new User();
          user.setName("John Doe");
          user.setEmail("john@example.com");

          session.persist(user);
          System.out.println("Persisted user (ID will be generated)");

          // 5. Commit - flushes changes to DB
          tx.commit();
          System.out.println("Committed. User ID: " + user.getId());

        } catch (Exception e) {
          tx.rollback();
          throw e;
        }
      }

      // Demonstrate find and dirty checking
      try (MiniSession session = sessionFactory.openSession()) {
        MiniTransaction tx = session.beginTransaction();

        try {
          // Find by ID (first query hits DB)
          User user = session.find(User.class, 1L);
          System.out.println("Found: " + user.getName());

          // Find same ID again (from cache - no SQL!)
          User sameUser = session.find(User.class, 1L);
          System.out.println("Same instance from cache: " + (user == sameUser));

          // Modify entity (dirty checking will detect this)
          user.setName("Jane Doe");

          // Query
          var users = session.createQuery(User.class)
              .where("email", "john@example.com")
              .getResultList();
          System.out.println("Found " + users.size() + " users by email");

          // Commit - auto-generates UPDATE for dirty entity
          tx.commit();
          System.out.println("Name updated via dirty checking!");

        } catch (Exception e) {
          tx.rollback();
          throw e;
        }
      }

      System.out.println("\n✅ Mini-Hibernate demo completed!");

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void createTable(MiniSessionFactory sf) {
    try (MiniSession session = sf.openSession()) {
      var conn = session.getConnection();
      conn.createStatement().execute("""
              DROP TABLE IF EXISTS users;
          """);
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
