package org.ltl.minihibernate.page;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A page of results from a query.
 * Contains the content, page information, and total element count.
 * 
 * <pre>
 * Page<User> page = repository.findAll(PageRequest.of(0, 10));
 * 
 * page.getContent();       // List of users
 * page.getTotalElements(); // Total count
 * page.getTotalPages();    // Total pages
 * page.hasNext();          // Has next page?
 * </pre>
 */
public class Page<T> implements Iterable<T> {
  
  private final List<T> content;
  private final Pageable pageable;
  private final long totalElements;
  
  public Page(List<T> content, Pageable pageable, long totalElements) {
    this.content = content;
    this.pageable = pageable;
    this.totalElements = totalElements;
  }
  
  public static <T> Page<T> empty() {
    return new Page<>(List.of(), PageRequest.of(0, 10), 0);
  }
  
  public static <T> Page<T> empty(Pageable pageable) {
    return new Page<>(List.of(), pageable, 0);
  }
  
  /** The page content. */
  public List<T> getContent() { return content; }
  
  /** Total number of elements across all pages. */
  public long getTotalElements() { return totalElements; }
  
  /** Total number of pages. */
  public int getTotalPages() {
    return pageable.getPageSize() == 0 ? 1 : (int) Math.ceil((double) totalElements / pageable.getPageSize());
  }
  
  /** Current page number (0-indexed). */
  public int getNumber() { return pageable.getPageNumber(); }
  
  /** Page size. */
  public int getSize() { return pageable.getPageSize(); }
  
  /** Number of elements in this page. */
  public int getNumberOfElements() { return content.size(); }
  
  /** Is this the first page? */
  public boolean isFirst() { return !hasPrevious(); }
  
  /** Is this the last page? */
  public boolean isLast() { return !hasNext(); }
  
  /** Has next page? */
  public boolean hasNext() { return getNumber() + 1 < getTotalPages(); }
  
  /** Has previous page? */
  public boolean hasPrevious() { return getNumber() > 0; }
  
  /** Is content empty? */
  public boolean isEmpty() { return content.isEmpty(); }
  
  /** Has any content? */
  public boolean hasContent() { return !content.isEmpty(); }
  
  /** Get the pageable for next page. */
  public Pageable nextPageable() { return hasNext() ? pageable.next() : Pageable.class.cast(null); }
  
  /** Get the pageable for previous page. */
  public Pageable previousPageable() { return hasPrevious() ? pageable.previous() : null; }
  
  /** Map content to another type. */
  public <U> Page<U> map(Function<? super T, ? extends U> converter) {
    List<U> converted = content.stream().map(converter).collect(Collectors.toList());
    return new Page<>(converted, pageable, totalElements);
  }
  
  @Override
  public Iterator<T> iterator() { return content.iterator(); }
  
  @Override
  public String toString() {
    return String.format("Page %d of %d containing %d elements (total: %d)",
        getNumber(), getTotalPages(), getNumberOfElements(), getTotalElements());
  }
}
