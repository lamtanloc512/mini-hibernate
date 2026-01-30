package org.ltl.minihibernate.repository;

import org.ltl.minihibernate.page.CursorPage;
import org.ltl.minihibernate.page.CursorPageable;
import org.ltl.minihibernate.page.Page;
import org.ltl.minihibernate.page.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Base repository interface similar to Spring Data JPA's JpaRepository.
 * Supports both offset-based and cursor-based pagination.
 * 
 * @param <T>  Entity type
 * @param <ID> Primary key type
 */
public interface MiniRepository<T, ID> {

  // ==================== CRUD Operations ====================
  
  T save(T entity);
  Optional<T> findById(ID id);
  List<T> findAll();
  void deleteById(ID id);
  boolean existsById(ID id);
  long count();

  // ==================== Offset-based Pagination ====================
  
  /**
   * Returns a page of entities.
   * 
   * <pre>
   * Page<User> page = repository.findAll(PageRequest.of(0, 10));
   * </pre>
   */
  Page<T> findAll(Pageable pageable);

  // ==================== Cursor-based Pagination ====================
  
  /**
   * Returns a cursor page of entities.
   * 
   * <pre>
   * CursorPage<User> page = repository.findAll(CursorPageable.first(10));
   * CursorPage<User> next = repository.findAll(CursorPageable.after(lastId, 10));
   * </pre>
   */
  CursorPage<T> findAll(CursorPageable cursorPageable);
}
