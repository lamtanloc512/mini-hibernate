package com.minihibernate.session;

import io.vavr.control.Try;

import java.sql.Connection;

/**
 * Manages database transaction boundaries.
 * 
 * Ensures ACID properties by wrapping JDBC transactions.
 */
public class MiniTransaction {
    
    private final Connection connection;
    private final MiniSession session;
    private boolean active = false;
    private boolean committed = false;
    private boolean rolledBack = false;
    
    MiniTransaction(Connection connection, MiniSession session) {
        this.connection = connection;
        this.session = session;
    }
    
    /**
     * Begins the transaction.
     */
    public void begin() {
        if (active) {
            throw new IllegalStateException("Transaction already active");
        }
        Try.run(() -> connection.setAutoCommit(false))
                .getOrElseThrow(e -> new RuntimeException("Failed to begin transaction", e));
        active = true;
    }
    
    /**
     * Commits the transaction.
     * Flushes all pending changes before commit.
     */
    public void commit() {
        checkActive();
        Try.run(() -> {
            session.flush();
            connection.commit();
            connection.setAutoCommit(true);
        }).getOrElseThrow(e -> new RuntimeException("Failed to commit transaction", e));
        active = false;
        committed = true;
    }
    
    /**
     * Rolls back the transaction.
     * Clears the persistence context.
     */
    public void rollback() {
        checkActive();
        Try.run(() -> {
            connection.rollback();
            connection.setAutoCommit(true);
        }).getOrElseThrow(e -> new RuntimeException("Failed to rollback", e));
        session.clear();
        active = false;
        rolledBack = true;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public boolean wasCommitted() {
        return committed;
    }
    
    public boolean wasRolledBack() {
        return rolledBack;
    }
    
    private void checkActive() {
        if (!active) {
            throw new IllegalStateException("Transaction is not active");
        }
    }
}
