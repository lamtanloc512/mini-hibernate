package org.ltl.minihibernate.spi;

/**
 * MySQL Dialect implementation.
 */
public class MySQLDialect implements Dialect {

  @Override
  public String getSqlType(Class<?> javaType) {
    if (javaType == Long.class || javaType == long.class) {
      return "BIGINT";
    } else if (javaType == Integer.class || javaType == int.class) {
      return "INT";
    } else if (javaType == String.class) {
      return "VARCHAR(255)";
    } else if (javaType == Boolean.class || javaType == boolean.class) {
      return "TINYINT(1)";
    }
    return "VARCHAR(255)";
  }

  @Override
  public String getIdentityColumnString() {
    return "AUTO_INCREMENT";
  }

  @Override
  public String getLimitString(String sql, int offset, int limit) {
    if (offset > 0) {
      return sql + " LIMIT " + limit + " OFFSET " + offset;
    }
    return sql + " LIMIT " + limit;
  }

  @Override
  public String getName() {
    return "MySQL";
  }
}
