package com.minihibernate.benchmark;

import com.minihibernate.api.MiniEntityManager;
import com.minihibernate.api.MiniEntityManagerFactory;
import com.minihibernate.example.User;
import com.minihibernate.example.UserRepository;
import com.minihibernate.internal.MiniEntityManagerFactoryImpl;
import com.minihibernate.repository.RepositoryFactory;

import java.sql.Connection;
import java.util.List;

/**
 * Simple benchmark for Mini-Hibernate operations.
 * 
 * Run: mvn test-compile exec:java
 * -Dexec.mainClass="com.minihibernate.benchmark.SimpleBenchmark"
 * -Dexec.classpathScope=test
 * 
 * Measures latency (µs/op) for common operations.
 */
public class SimpleBenchmark {

  private static final int WARMUP_ITERATIONS = 500;
  private static final int BENCHMARK_ITERATIONS = 1000;

  private MiniEntityManagerFactory factory;
  private UserRepository userRepo;

  public static void main(String[] args) throws Exception {
    SimpleBenchmark benchmark = new SimpleBenchmark();
    benchmark.setup();

    try {
      benchmark.runAllBenchmarks();
    } finally {
      benchmark.tearDown();
    }
  }

  private void setup() throws Exception {
    System.out.println("=== Mini-Hibernate Benchmark ===\n");
    System.out.println("Setting up H2 in-memory database...");

    factory = MiniEntityManagerFactoryImpl.builder()
        .url("jdbc:h2:mem:benchmark;DB_CLOSE_DELAY=-1")
        .username("sa")
        .password("")
        .addEntityClass(User.class)
        .build();

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

      conn.createStatement().execute("""
          INSERT INTO users (name, email, age) VALUES
          ('Alice', 'alice@example.com', 28),
          ('Bob', 'bob@example.com', 35),
          ('Charlie', 'charlie@example.com', 22),
          ('Diana', 'diana@example.com', 30),
          ('Eva', 'eva@example.com', 45)
          """);
    }

    RepositoryFactory repoFactory = new RepositoryFactory(factory);
    userRepo = repoFactory.createRepository(UserRepository.class);

    System.out.println("Setup complete. Running benchmarks...\n");
    System.out.println("Warmup: " + WARMUP_ITERATIONS + " iterations");
    System.out.println("Benchmark: " + BENCHMARK_ITERATIONS + " iterations\n");
    System.out.println("-".repeat(60));
    System.out.printf("%-35s %12s %12s%n", "Operation", "Avg (µs)", "Ops/sec");
    System.out.println("-".repeat(60));
  }

  private void tearDown() {
    if (factory != null) {
      try {
        factory.close();
      } catch (Exception ignored) {
      }
    }
  }

  private void runAllBenchmarks() {
    benchmark("findById(1L)", () -> userRepo.findById(1L));
    benchmark("findAll()", () -> userRepo.findAll());
    benchmark("count()", () -> userRepo.count());
    benchmark("existsById(1L)", () -> userRepo.existsById(1L));
    benchmark("@Query: findByEmail", () -> userRepo.findByEmail("alice@example.com"));
    benchmark("@Query: findByAgeBetween", () -> userRepo.findByAgeBetween(25, 35));
    benchmark("@Query: findByNameContaining", () -> userRepo.findByNameContaining("%a%"));
    benchmark("@Query: findByAgeGreaterThan", () -> userRepo.findByAgeGreaterThan(30));
    benchmark("save + delete", this::saveAndDelete);

    System.out.println("-".repeat(60));
    System.out.println("\n✅ Benchmark complete!");
  }

  private void benchmark(String name, Runnable operation) {
    // Warmup
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      operation.run();
    }

    // Benchmark
    long startTime = System.nanoTime();
    for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
      operation.run();
    }
    long elapsed = System.nanoTime() - startTime;

    double avgMicros = (elapsed / 1000.0) / BENCHMARK_ITERATIONS;
    double opsPerSec = 1_000_000.0 / avgMicros;

    System.out.printf("%-35s %12.2f %12.0f%n", name, avgMicros, opsPerSec);
  }

  private void saveAndDelete() {
    User user = new User();
    user.setName("Benchmark User");
    user.setEmail("bench@test.com");
    user.setAge(25);

    User saved = userRepo.save(user);
    userRepo.deleteById(saved.getId());
  }
}
