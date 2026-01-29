package org.ltl.minihibernate.api;

import java.io.Closeable;

/**
 * MiniEntityManagerFactory - Factory interface.
 * 
 * Like JPA's EntityManagerFactory interface.
 */
public interface MiniEntityManagerFactory extends Closeable {
    
    /**
     * Creates a new EntityManager.
     */
    MiniEntityManager createEntityManager();
    
    /**
     * Checks if the factory is still open.
     */
    boolean isOpen();
}
