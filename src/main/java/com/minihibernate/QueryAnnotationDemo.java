package com.minihibernate;

import com.minihibernate.api.MiniEntityManager;
import com.minihibernate.api.MiniEntityManagerFactory;
import com.minihibernate.example.User;
import com.minihibernate.example.UserRepository;
import com.minihibernate.internal.MiniEntityManagerFactoryImpl;
import com.minihibernate.repository.RepositoryFactory;

import java.sql.Connection;
import java.util.List;

/**
 * Demo showing @Query annotation - the MAGIC of Spring Data JPA!
 * 
 * This demonstrates how we can:
 * 1. Define an interface with @Query methods
 * 2. Get an auto-generated implementation via Dynamic Proxy
 * 3. Execute custom SQL without writing any implementation code
 */
public class QueryAnnotationDemo {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== @Query Annotation Demo ===\n");
        
        // 1. Create factory
        MiniEntityManagerFactory factory = MiniEntityManagerFactoryImpl.builder()
                .url("jdbc:mysql://localhost:3307/test_db")
                .username("root")
                .password("p@ssword")
                .addEntityClass(User.class)
                .build();
        
        try {
            // Setup: Create table and insert test data
            setupTestData(factory);
            
            // 2. Create repository using RepositoryFactory
            RepositoryFactory repoFactory = new RepositoryFactory(factory);
            UserRepository userRepo = repoFactory.createRepository(UserRepository.class);
            
            System.out.println("Repository type: " + userRepo.getClass().getName());
            System.out.println("  → This is a PROXY, not a real class!\n");
            
            // 3. Use standard repository methods
            System.out.println("--- Standard MiniRepository methods ---");
            
            long count = userRepo.count();
            System.out.println("Total users: " + count);
            
            List<User> allUsers = (List<User>) userRepo.findAll();
            System.out.println("All users: " + allUsers.size());
            
            // 4. Use @Query methods
            System.out.println("\n--- Custom @Query methods ---");
            
            // findByEmail
            userRepo.findByEmail("alice@example.com")
                    .ifPresentOrElse(
                            u -> System.out.println("findByEmail('alice@example.com'): " + u),
                            () -> System.out.println("findByEmail: Not found")
                    );
            
            // findByNameContaining
            List<User> usersWithA = userRepo.findByNameContaining("%a%");
            System.out.println("findByNameContaining('%a%'): " + usersWithA.size() + " users");
            usersWithA.forEach(u -> System.out.println("  - " + u.getName()));
            
            // findByAgeBetween
            List<User> usersInRange = userRepo.findByAgeBetween(25, 35);
            System.out.println("findByAgeBetween(25, 35): " + usersInRange.size() + " users");
            usersInRange.forEach(u -> System.out.println("  - " + u.getName() + " (age: " + u.getAge() + ")"));
            
            // findByAgeGreaterThan
            List<User> olderUsers = userRepo.findByAgeGreaterThan(30);
            System.out.println("findByAgeGreaterThan(30): " + olderUsers.size() + " users");
            
            System.out.println("\n✅ @Query Demo completed!");
            System.out.println("\nKey insight: We defined ONLY an interface, but got a working implementation!");
            System.out.println("This is exactly how Spring Data JPA works - using Java Dynamic Proxy.");
            
        } finally {
            factory.close();
        }
    }
    
    private static void setupTestData(MiniEntityManagerFactory factory) throws Exception {
        try (MiniEntityManager em = factory.createEntityManager()) {
            Connection conn = em.unwrap(Connection.class);
            
            // Recreate table
            conn.createStatement().execute("DROP TABLE IF EXISTS users");
            conn.createStatement().execute("""
                CREATE TABLE users (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(255),
                    email VARCHAR(255),
                    age INT
                )
            """);
            
            // Insert test data
            conn.createStatement().execute("""
                INSERT INTO users (name, email, age) VALUES 
                ('Alice', 'alice@example.com', 28),
                ('Bob', 'bob@example.com', 35),
                ('Charlie', 'charlie@example.com', 22),
                ('Diana', 'diana@example.com', 30),
                ('Eva', 'eva@example.com', 45)
            """);
            
            System.out.println("Test data created: 5 users\n");
        }
    }
}
