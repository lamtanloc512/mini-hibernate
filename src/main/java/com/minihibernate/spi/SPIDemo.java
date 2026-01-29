package com.minihibernate.spi;

import java.util.ServiceLoader;

/**
 * Demo showing how SPI (Service Provider Interface) works.
 * 
 * This is how Hibernate and Spring load implementations dynamically.
 */
public class SPIDemo {
    
    public static void main(String[] args) {
        System.out.println("=== SPI Demo: Loading Dialects ===\n");
        
        // ServiceLoader automatically finds all implementations
        // listed in META-INF/services/com.minihibernate.spi.Dialect
        ServiceLoader<Dialect> dialects = ServiceLoader.load(Dialect.class);
        
        System.out.println("Found dialects:");
        for (Dialect dialect : dialects) {
            System.out.println("  • " + dialect.getName() + " → " + dialect.getClass().getName());
            System.out.println("    - Long type: " + dialect.getSqlType(Long.class));
            System.out.println("    - Identity: " + dialect.getIdentityColumnString());
            System.out.println();
        }
        
        // Use case: Auto-detect dialect based on JDBC URL
        String jdbcUrl = "jdbc:mysql://localhost:3306/test";
        Dialect selectedDialect = detectDialect(jdbcUrl);
        System.out.println("Selected dialect for " + jdbcUrl + ": " + selectedDialect.getName());
    }
    
    /**
     * Real-world example: Auto-detect dialect from JDBC URL.
     * This is similar to what Hibernate does.
     */
    private static Dialect detectDialect(String jdbcUrl) {
        ServiceLoader<Dialect> dialects = ServiceLoader.load(Dialect.class);
        
        for (Dialect dialect : dialects) {
            if (jdbcUrl.contains("mysql") && dialect.getName().equals("MySQL")) {
                return dialect;
            }
            if (jdbcUrl.contains("h2") && dialect.getName().equals("H2")) {
                return dialect;
            }
        }
        
        // Default
        return dialects.iterator().next();
    }
}
