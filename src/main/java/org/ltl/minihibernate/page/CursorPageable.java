package org.ltl.minihibernate.page;

/**
 * Cursor-based pagination request.
 * Uses a cursor (typically last seen ID) instead of offset.
 * 
 * <pre>
 * // First page
 * CursorPageable cursor = CursorPageable.first(10);
 * 
 * // Next page (using cursor from last item)
 * CursorPageable cursor = CursorPageable.after(lastId, 10);
 * </pre>
 */
public class CursorPageable {
  
  public enum Direction {
    AFTER,  // Get items after cursor (forward)
    BEFORE  // Get items before cursor (backward)
  }
  
  private final Object cursor;
  private final int size;
  private final Direction direction;
  private final Sort sort;
  
  private CursorPageable(Object cursor, int size, Direction direction, Sort sort) {
    this.cursor = cursor;
    this.size = size;
    this.direction = direction;
    this.sort = sort != null ? sort : Sort.by("id");
  }
  
  /**
   * Create first page request (no cursor).
   */
  public static CursorPageable first(int size) {
    return new CursorPageable(null, size, Direction.AFTER, null);
  }
  
  /**
   * Create request for items AFTER the given cursor.
   */
  public static CursorPageable after(Object cursor, int size) {
    return new CursorPageable(cursor, size, Direction.AFTER, null);
  }
  
  /**
   * Create request for items BEFORE the given cursor.
   */
  public static CursorPageable before(Object cursor, int size) {
    return new CursorPageable(cursor, size, Direction.BEFORE, null);
  }
  
  public CursorPageable withSort(Sort sort) {
    return new CursorPageable(cursor, size, direction, sort);
  }
  
  public Object getCursor() { return cursor; }
  public int getSize() { return size; }
  public Direction getDirection() { return direction; }
  public Sort getSort() { return sort; }
  public boolean hasCursor() { return cursor != null; }
  public boolean isForward() { return direction == Direction.AFTER; }
  
  @Override
  public String toString() {
    return "CursorPageable[cursor=" + cursor + ", size=" + size + ", direction=" + direction + "]";
  }
}
