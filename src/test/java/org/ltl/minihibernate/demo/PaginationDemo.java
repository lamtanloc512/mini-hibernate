package org.ltl.minihibernate.demo;

import org.ltl.minihibernate.api.MiniEntityManager;
import org.ltl.minihibernate.api.MiniEntityManagerFactory;
import org.ltl.minihibernate.config.MiniConfiguration;
import org.ltl.minihibernate.example.User;
import org.ltl.minihibernate.example.UserRepository;
import org.ltl.minihibernate.page.*;
import org.ltl.minihibernate.repository.RepositoryFactory;

import java.sql.Connection;

/**
 * Demo: Offset-based and Cursor-based pagination.
 */
public class PaginationDemo {

  public static void main(String[] args) throws Exception {
    System.out.println("=== Pagination Demo ===\n");

    // Setup
    MiniEntityManagerFactory factory = new MiniConfiguration()
        .setProperty(MiniConfiguration.URL, "jdbc:h2:mem:pagedemo;DB_CLOSE_DELAY=-1")
        .setProperty(MiniConfiguration.USERNAME, "sa")
        .setProperty(MiniConfiguration.PASSWORD, "")
        .addAnnotatedClass(User.class)
        .buildSessionFactory();

    // Create table and insert test data
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

      // Insert 25 test users
      em.getTransaction().begin();
      for (int i = 1; i <= 25; i++) {
        User user = new User("User" + i, "user" + i + "@example.com");
        user.setAge(20 + i);
        em.persist(user);
      }
      em.getTransaction().commit();
    }

    UserRepository repository = new RepositoryFactory(factory).createRepository(UserRepository.class);

    // ==================== Offset-based Pagination ====================
    System.out.println("--- Offset-based Pagination ---");

    Page<User> page0 = repository.findAll(PageRequest.of(0, 10));
    System.out.println("Page 0: " + page0);
    System.out.println("  Content: " + page0.getContent().stream().map(User::getName).toList());
    System.out.println("  Total pages: " + page0.getTotalPages());
    System.out.println("  Has next: " + page0.hasNext());

    Page<User> page1 = repository.findAll(PageRequest.of(1, 10));
    System.out.println("\nPage 1: " + page1);
    System.out.println("  Content: " + page1.getContent().stream().map(User::getName).toList());

    // ==================== Cursor-based Pagination ====================
    System.out.println("\n--- Cursor-based Pagination ---");

    CursorPage<User> cursor1 = repository.findAll(CursorPageable.first(10));
    System.out.println("First cursor page:");
    System.out.println("  Content: " + cursor1.getContent().stream().map(User::getName).toList());
    System.out.println("  Has next: " + cursor1.hasNext());
    System.out.println("  Next cursor: " + cursor1.getNextCursor());

    CursorPage<User> cursor2 = repository.findAll(CursorPageable.after(cursor1.getNextCursor(), 10));
    System.out.println("\nNext cursor page (after id=" + cursor1.getNextCursor() + "):");
    System.out.println("  Content: " + cursor2.getContent().stream().map(User::getName).toList());
    System.out.println("  Has next: " + cursor2.hasNext());

    factory.close();
    System.out.println("\n✅ Pagination Demo complete!");
  }
}
