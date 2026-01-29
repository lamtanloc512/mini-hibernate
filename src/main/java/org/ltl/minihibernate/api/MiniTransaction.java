package org.ltl.minihibernate.api;

/**
 * MiniTransaction - Transaction interface.
 * 
 * Like JPA's EntityTransaction interface.
 */
public interface MiniTransaction {
    
    void begin();
    
    void commit();
    
    void rollback();
    
    boolean isActive();
}
