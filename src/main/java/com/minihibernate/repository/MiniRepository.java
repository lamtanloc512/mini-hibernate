package com.minihibernate.repository;

import java.util.List;
import java.util.Optional;

/**
 * Base repository interface similar to Spring Data JPA's JpaRepository.
 * 
 * @param <T> Entity type
 * @param <ID> Primary key type
 */
public interface MiniRepository<T, ID> {
    
    /**
     * Saves an entity (insert or update).
     */
    T save(T entity);
    
    /**
     * Finds an entity by ID.
     */
    Optional<T> findById(ID id);
    
    /**
     * Returns all entities.
     */
    List<T> findAll();
    
    /**
     * Deletes an entity by ID.
     */
    void deleteById(ID id);
    
    /**
     * Checks if an entity exists by ID.
     */
    boolean existsById(ID id);
    
    /**
     * Returns count of entities.
     */
    long count();
}
