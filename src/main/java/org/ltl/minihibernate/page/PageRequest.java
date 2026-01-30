package org.ltl.minihibernate.page;

/**
 * Implementation of Pageable for creating page requests.
 * 
 * <pre>
 * // Page 0, 10 items per page
 * Pageable pageable = PageRequest.of(0, 10);
 * 
 * // With sorting
 * Pageable pageable = PageRequest.of(0, 10, Sort.by("name"));
 * </pre>
 */
public class PageRequest implements Pageable {
  
  private final int page;
  private final int size;
  private final Sort sort;
  
  private PageRequest(int page, int size, Sort sort) {
    if (page < 0) throw new IllegalArgumentException("Page index must not be less than zero");
    if (size < 1) throw new IllegalArgumentException("Page size must not be less than one");
    this.page = page;
    this.size = size;
    this.sort = sort;
  }
  
  public static PageRequest of(int page, int size) {
    return new PageRequest(page, size, null);
  }
  
  public static PageRequest of(int page, int size, Sort sort) {
    return new PageRequest(page, size, sort);
  }
  
  public static PageRequest ofSize(int size) {
    return new PageRequest(0, size, null);
  }
  
  @Override
  public int getPageNumber() { return page; }
  
  @Override
  public int getPageSize() { return size; }
  
  @Override
  public long getOffset() { return (long) page * size; }
  
  @Override
  public Sort getSort() { return sort; }
  
  @Override
  public Pageable next() { return new PageRequest(page + 1, size, sort); }
  
  @Override
  public Pageable previous() { return page == 0 ? this : new PageRequest(page - 1, size, sort); }
  
  @Override
  public Pageable first() { return new PageRequest(0, size, sort); }
  
  public PageRequest withSort(Sort sort) {
    return new PageRequest(page, size, sort);
  }
  
  @Override
  public String toString() {
    return "PageRequest[page=" + page + ", size=" + size + ", sort=" + sort + "]";
  }
}
