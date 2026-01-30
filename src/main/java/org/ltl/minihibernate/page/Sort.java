package org.ltl.minihibernate.page;

/**
 * Sort information for queries.
 */
public class Sort {
  
  public enum Direction {
    ASC, DESC
  }
  
  private final String property;
  private final Direction direction;
  
  private Sort(String property, Direction direction) {
    this.property = property;
    this.direction = direction;
  }
  
  public static Sort by(String property) {
    return new Sort(property, Direction.ASC);
  }
  
  public static Sort by(Direction direction, String property) {
    return new Sort(property, direction);
  }
  
  public Sort ascending() {
    return new Sort(property, Direction.ASC);
  }
  
  public Sort descending() {
    return new Sort(property, Direction.DESC);
  }
  
  public String getProperty() { return property; }
  public Direction getDirection() { return direction; }
  public boolean isAscending() { return direction == Direction.ASC; }
  
  public String toSql() {
    return property + " " + direction.name();
  }
  
  public static Sort unsorted() {
    return null;
  }
}
