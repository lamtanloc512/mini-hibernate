package com.minihibernate.spi;

/**
 * SPI interface for SQL dialects.
 * 
 * Different databases have different SQL syntax.
 * This allows plugging in database-specific SQL generators.
 */
public interface Dialect {
    
    /**
     * Gets the SQL type for the given Java type.
     */
    String getSqlType(Class<?> javaType);
    
    /**
     * Gets the identity column definition.
     * MySQL: AUTO_INCREMENT
     * PostgreSQL: SERIAL
     * H2: AUTO_INCREMENT
     */
    String getIdentityColumnString();
    
    /**
     * Gets the LIMIT clause syntax.
     * MySQL/PostgreSQL: LIMIT n OFFSET m
     * Oracle: ROWNUM, FETCH FIRST
     */
    String getLimitString(String sql, int offset, int limit);
    
    /**
     * Dialect name for identification.
     */
    String getName();
}
