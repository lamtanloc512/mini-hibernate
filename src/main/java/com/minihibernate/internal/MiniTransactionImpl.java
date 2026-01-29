package com.minihibernate.internal;

import com.minihibernate.api.MiniTransaction;
import io.vavr.control.Try;

import java.sql.Connection;

/**
 * MiniTransactionImpl - Transaction implementation.
 * 
 * Like Hibernate's TransactionImpl.
 */
public class MiniTransactionImpl implements MiniTransaction {
    
    private final Connection connection;
    private final MiniEntityManagerImpl entityManager;
    private boolean active = false;
    
    MiniTransactionImpl(Connection connection, MiniEntityManagerImpl entityManager) {
        this.connection = connection;
        this.entityManager = entityManager;
    }
    
    @Override
    public void begin() {
        if (active) {
            throw new IllegalStateException("Transaction already active");
        }
        Try.run(() -> connection.setAutoCommit(false))
                .getOrElseThrow(e -> new RuntimeException("Failed to begin transaction", e));
        active = true;
    }
    
    @Override
    public void commit() {
        checkActive();
        Try.run(() -> {
            entityManager.flush();
            connection.commit();
            connection.setAutoCommit(true);
        }).getOrElseThrow(e -> new RuntimeException("Failed to commit", e));
        active = false;
    }
    
    @Override
    public void rollback() {
        checkActive();
        Try.run(() -> {
            connection.rollback();
            connection.setAutoCommit(true);
        }).getOrElseThrow(e -> new RuntimeException("Failed to rollback", e));
        entityManager.clear();
        active = false;
    }
    
    @Override
    public boolean isActive() {
        return active;
    }
    
    private void checkActive() {
        if (!active) {
            throw new IllegalStateException("No active transaction");
        }
    }
}
