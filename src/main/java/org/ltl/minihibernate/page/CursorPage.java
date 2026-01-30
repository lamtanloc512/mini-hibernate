package org.ltl.minihibernate.page;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A page of results using cursor-based pagination.
 * 
 * <pre>
 * CursorPage<User> page = repository.findAll(CursorPageable.first(10));
 * 
 * page.getContent();     // List of users
 * page.hasNext();        // Has more items?
 * page.getNextCursor();  // Cursor for next page
 * </pre>
 */
public class CursorPage<T> implements Iterable<T> {
  
  private final List<T> content;
  private final CursorPageable request;
  private final Object nextCursor;
  private final Object previousCursor;
  private final boolean hasNext;
  private final boolean hasPrevious;
  
  public CursorPage(List<T> content, CursorPageable request, 
                    Object nextCursor, Object previousCursor,
                    boolean hasNext, boolean hasPrevious) {
    this.content = content;
    this.request = request;
    this.nextCursor = nextCursor;
    this.previousCursor = previousCursor;
    this.hasNext = hasNext;
    this.hasPrevious = hasPrevious;
  }
  
  /**
   * Create a CursorPage from content.
   * Automatically determines cursors from first/last items.
   */
  public static <T> CursorPage<T> of(List<T> content, CursorPageable request, 
                                      Function<T, Object> cursorExtractor, boolean hasMore) {
    Object nextCursor = content.isEmpty() ? null : cursorExtractor.apply(content.get(content.size() - 1));
    Object prevCursor = content.isEmpty() ? null : cursorExtractor.apply(content.get(0));
    boolean hasPrev = request.hasCursor() && request.isForward();
    
    return new CursorPage<>(content, request, nextCursor, prevCursor, hasMore, hasPrev);
  }
  
  public List<T> getContent() { return content; }
  public int getSize() { return content.size(); }
  public boolean isEmpty() { return content.isEmpty(); }
  public boolean hasContent() { return !content.isEmpty(); }
  
  public boolean hasNext() { return hasNext; }
  public boolean hasPrevious() { return hasPrevious; }
  
  public Object getNextCursor() { return nextCursor; }
  public Object getPreviousCursor() { return previousCursor; }
  
  /** Get pageable for next page. */
  public CursorPageable nextPageable() {
    return hasNext ? CursorPageable.after(nextCursor, request.getSize()) : null;
  }
  
  /** Get pageable for previous page. */
  public CursorPageable previousPageable() {
    return hasPrevious ? CursorPageable.before(previousCursor, request.getSize()) : null;
  }
  
  /** Map content to another type. */
  public <U> CursorPage<U> map(Function<? super T, ? extends U> converter) {
    List<U> converted = content.stream().map(converter).collect(Collectors.toList());
    return new CursorPage<>(converted, request, nextCursor, previousCursor, hasNext, hasPrevious);
  }
  
  @Override
  public Iterator<T> iterator() { return content.iterator(); }
  
  @Override
  public String toString() {
    return "CursorPage[size=" + content.size() + ", hasNext=" + hasNext + 
        ", cursor=" + nextCursor + "]";
  }
}
