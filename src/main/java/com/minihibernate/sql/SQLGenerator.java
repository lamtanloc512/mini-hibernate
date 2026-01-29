package com.minihibernate.sql;

import com.minihibernate.metadata.EntityMetadata;
import com.minihibernate.metadata.FieldMetadata;
import io.vavr.collection.List;

/**
 * Generates SQL statements from entity metadata.
 * 
 * This is a simplified single-dialect generator.
 * Real Hibernate uses Dialect classes for database-specific SQL.
 */
public class SQLGenerator {
    
    /**
     * Generates INSERT statement.
     * 
     * Example: INSERT INTO users (name, email) VALUES (?, ?)
     */
    public String generateInsert(EntityMetadata metadata) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(metadata.getTableName()).append(" (");
        
        java.util.List<FieldMetadata> columns = metadata.getAllColumns();
        boolean hasGeneratedId = metadata.getIdField().isGeneratedValue();
        
        List<String> columnNames = List.ofAll(columns)
                .filter(f -> !(f.isId() && hasGeneratedId))
                .map(FieldMetadata::getColumnName);
        
        sql.append(columnNames.mkString(", "));
        sql.append(") VALUES (");
        sql.append(columnNames.map(c -> "?").mkString(", "));
        sql.append(")");
        
        return sql.toString();
    }
    
    /**
     * Generates SELECT by ID statement.
     * 
     * Example: SELECT id, name, email FROM users WHERE id = ?
     */
    public String generateSelectById(EntityMetadata metadata) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        
        List<String> columnNames = List.ofAll(metadata.getAllColumns())
                .map(FieldMetadata::getColumnName);
        
        sql.append(columnNames.mkString(", "));
        sql.append(" FROM ").append(metadata.getTableName());
        sql.append(" WHERE ").append(metadata.getIdField().getColumnName()).append(" = ?");
        
        return sql.toString();
    }
    
    /**
     * Generates SELECT all statement.
     * 
     * Example: SELECT id, name, email FROM users
     */
    public String generateSelectAll(EntityMetadata metadata) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        
        List<String> columnNames = List.ofAll(metadata.getAllColumns())
                .map(FieldMetadata::getColumnName);
        
        sql.append(columnNames.mkString(", "));
        sql.append(" FROM ").append(metadata.getTableName());
        
        return sql.toString();
    }
    
    /**
     * Generates UPDATE statement.
     * 
     * Example: UPDATE users SET name = ?, email = ? WHERE id = ?
     */
    public String generateUpdate(EntityMetadata metadata) {
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(metadata.getTableName()).append(" SET ");
        
        // Non-ID columns
        List<String> setClauses = List.ofAll(metadata.getColumns())
                .map(f -> f.getColumnName() + " = ?");
        
        sql.append(setClauses.mkString(", "));
        sql.append(" WHERE ").append(metadata.getIdField().getColumnName()).append(" = ?");
        
        return sql.toString();
    }
    
    /**
     * Generates DELETE statement.
     * 
     * Example: DELETE FROM users WHERE id = ?
     */
    public String generateDelete(EntityMetadata metadata) {
        return "DELETE FROM " + metadata.getTableName() + 
                " WHERE " + metadata.getIdField().getColumnName() + " = ?";
    }
    
    /**
     * Generates COUNT statement.
     * 
     * Example: SELECT COUNT(*) FROM users
     */
    public String generateCount(EntityMetadata metadata) {
        return "SELECT COUNT(*) FROM " + metadata.getTableName();
    }
    
    /**
     * Generates SELECT with WHERE clause.
     * 
     * Example: SELECT id, name, email FROM users WHERE status = ?
     */
    public String generateSelectWhere(EntityMetadata metadata, String whereClause) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        
        List<String> columnNames = List.ofAll(metadata.getAllColumns())
                .map(FieldMetadata::getColumnName);
        
        sql.append(columnNames.mkString(", "));
        sql.append(" FROM ").append(metadata.getTableName());
        sql.append(" WHERE ").append(whereClause);
        
        return sql.toString();
    }
}
