package org.ltl.minihibernate.example;

import org.ltl.minihibernate.annotation.Query;
import org.ltl.minihibernate.repository.MiniRepository;

import java.util.List;
import java.util.Optional;

/**
 * Example repository showing @Query annotation usage.
 * 
 * This interface is automatically implemented by RepositoryFactory
 * using Java Dynamic Proxy.
 */
public interface UserRepository extends MiniRepository<User, Long> {

  /**
   * Custom query using @Query annotation.
   * ?1 refers to the first parameter (email).
   */
  @Query("SELECT * FROM users WHERE email = ?1")
  Optional<User> findByEmail(String email);

  /**
   * Find users by name pattern.
   */
  @Query("SELECT * FROM users WHERE name LIKE ?1")
  List<User> findByNameContaining(String pattern);

  /**
   * Find users by age range.
   */
  @Query("SELECT * FROM users WHERE age >= ?1 AND age <= ?2")
  List<User> findByAgeBetween(int minAge, int maxAge);

  /**
   * Find users older than given age.
   */
  @Query("SELECT * FROM users WHERE age > ?1 ORDER BY age DESC")
  List<User> findByAgeGreaterThan(int age);

  /**
   * Count users by name.
   */
  @Query("SELECT COUNT(*) FROM users WHERE name = ?1")
  User countByName(String name); // Returns single result
}
