package org.ltl.minihibernate.page;

/**
 * Interface for offset-based pagination information.
 * Similar to Spring Data's Pageable.
 */
public interface Pageable {
  
  /**
   * Returns the page number (0-indexed).
   */
  int getPageNumber();
  
  /**
   * Returns the page size (number of items per page).
   */
  int getPageSize();
  
  /**
   * Returns the offset for the query (page * size).
   */
  long getOffset();
  
  /**
   * Returns the sort information, or null if unsorted.
   */
  Sort getSort();
  
  /**
   * Returns the next Pageable.
   */
  Pageable next();
  
  /**
   * Returns the previous Pageable.
   */
  Pageable previous();
  
  /**
   * Returns the first Pageable.
   */
  Pageable first();
}
