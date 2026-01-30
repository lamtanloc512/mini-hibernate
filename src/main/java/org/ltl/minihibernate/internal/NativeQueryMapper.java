package org.ltl.minihibernate.internal;

import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.metadata.FieldMetadata;
import org.ltl.minihibernate.metadata.MetadataParser;

import java.lang.reflect.Constructor;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

/**
 * Maps native query results to entities and DTOs.
 * Supports:
 * - Entity mapping
 * - Constructor expression (SELECT new com.example.DTO(...) ...)
 * - @SqlResultSetMapping (basic support)
 */
public class NativeQueryMapper {

    private final MetadataParser metadataParser;
    private final Map<Class<?>, EntityMetadata> metadataCache = new HashMap<>();

    public NativeQueryMapper(MetadataParser metadataParser) {
        this.metadataParser = metadataParser;
    }

    /**
     * Map a ResultSet row to an entity.
     */
    public <T> T mapToEntity(ResultSet rs, Class<T> entityClass) throws SQLException {
        EntityMetadata metadata = getMetadata(entityClass);
        T entity;

        try {
            entity = entityClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate entity: " + entityClass.getName(), e);
        }

        ResultSetMetaData rsMetaData = rs.getMetaData();
        int columnCount = rsMetaData.getColumnCount();

        // Create column name to index map (case-insensitive)
        Map<String, Integer> columnIndexMap = new HashMap<>();
        for (int i = 1; i <= columnCount; i++) {
            columnIndexMap.put(rsMetaData.getColumnName(i).toLowerCase(), i);
            String label = rsMetaData.getColumnLabel(i);
            if (!label.equals(rsMetaData.getColumnName(i))) {
                columnIndexMap.put(label.toLowerCase(), i);
            }
        }

        for (FieldMetadata field : metadata.getPersistableFields()) {
            String columnName = field.getColumnName().toLowerCase();
            Integer columnIndex = columnIndexMap.get(columnName);

            if (columnIndex != null) {
                Object value = rs.getObject(columnIndex);
                value = convertType(value, field.getJavaType());
                field.setValue(entity, value);
            }
        }

        return entity;
    }

    /**
     * Map a ResultSet row using constructor expression.
     * Pattern: SELECT new com.example.DTO(param1, param2, ...) FROM ...
     */
    public <T> T mapToDTO(ResultSet rs, String constructorExpression, Class<T> dtoClass) throws SQLException {
        // Parse constructor expression: "new com.example.DTO(param1, param2, ...)"
        ConstructorExpression expr = parseConstructorExpression(constructorExpression);

        if (!expr.className.equals(dtoClass.getName())) {
            throw new IllegalArgumentException(
                "Constructor expression class (" + expr.className +
                ") doesn't match DTO class (" + dtoClass.getName() + ")");
        }

        // Get constructor
        Constructor<T> constructor;
        try {
            constructor = dtoClass.getDeclaredConstructor(expr.paramTypes.toArray(new Class[0]));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(
                "No matching constructor found for DTO: " + dtoClass.getName() +
                " with parameters: " + expr.paramTypes, e);
        }

        // Map result set columns to constructor parameters
        ResultSetMetaData rsMetaData = rs.getMetaData();
        int columnCount = rsMetaData.getColumnCount();

        // Create column name to index map
        Map<String, Integer> columnIndexMap = new HashMap<>();
        for (int i = 1; i <= columnCount; i++) {
            String label = rsMetaData.getColumnLabel(i).toLowerCase();
            columnIndexMap.put(label, i);
        }

        Object[] args = new Object[expr.paramNames.size()];
        for (int i = 0; i < expr.paramNames.size(); i++) {
            String paramName = expr.paramNames.get(i);
            Class<?> paramType = expr.paramTypes.get(i);

            Integer columnIndex = columnIndexMap.get(paramName.toLowerCase());
            if (columnIndex == null) {
                // Try to find by order if column name doesn't match
                columnIndex = i + 1;
            }

            if (columnIndex <= columnCount) {
                Object value = rs.getObject(columnIndex);
                value = convertType(value, paramType);
                args[i] = value;
            } else {
                args[i] = null;
            }
        }

        try {
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate DTO: " + dtoClass.getName(), e);
        }
    }

    /**
     * Map a ResultSet row to a Map (scalar results).
     */
    public <T extends Map<String, Object>> T mapToMap(ResultSet rs, T result) throws SQLException {
        ResultSetMetaData rsMetaData = rs.getMetaData();
        int columnCount = rsMetaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            String columnName = rsMetaData.getColumnLabel(i);
            Object value = rs.getObject(i);
            result.put(columnName, value);
        }

        return result;
    }

    /**
     * Parse a constructor expression.
     * Example: "new com.example.UserDTO(u.id, u.name)" -> {className, [u.id, u.name], [Long.class, String.class]}
     */
    private ConstructorExpression parseConstructorExpression(String expression) {
        // Remove "new " prefix if present
        String trimmed = expression.trim();
        if (trimmed.startsWith("new ")) {
            trimmed = trimmed.substring(4).trim();
        }

        // Find the class name and parameters
        int parenStart = trimmed.indexOf('(');
        int parenEnd = trimmed.lastIndexOf(')');

        if (parenStart < 0 || parenEnd < 0) {
            throw new IllegalArgumentException("Invalid constructor expression: " + expression);
        }

        String className = trimmed.substring(0, parenStart).trim();
        String paramsStr = trimmed.substring(parenStart + 1, parenEnd).trim();

        List<String> paramNames = new ArrayList<>();
        List<Class<?>> paramTypes = new ArrayList<>();

        // Parse parameters (split by comma, respecting parentheses)
        int depth = 0;
        int lastSplit = 0;
        for (int i = 0; i <= paramsStr.length(); i++) {
            if (i == paramsStr.length() || (paramsStr.charAt(i) == ',' && depth == 0)) {
                String param = paramsStr.substring(lastSplit, i).trim();
                if (!param.isEmpty()) {
                    paramNames.add(param);
                    paramTypes.add(inferType(param));
                }
                lastSplit = i + 1;
            } else if (paramsStr.charAt(i) == '(') {
                depth++;
            } else if (paramsStr.charAt(i) == ')') {
                depth--;
            }
        }

        return new ConstructorExpression(className, paramNames, paramTypes);
    }

    /**
     * Infer Java type from parameter expression.
     */
    private Class<?> inferType(String param) {
        // Handle qualified names like "u.id", "o.amount"
        if (param.contains(".")) {
            param = param.substring(param.lastIndexOf('.') + 1);
        }

        // Try to infer from name patterns
        return switch (param.toLowerCase()) {
            case "id", "count", "quantity", "age", "amount", "price" -> Long.class;
            case "name", "email", "title", "description", "username" -> String.class;
            case "active", "enabled", "verified" -> Boolean.class;
            case "createdat", "updatedat", "birthdate" -> java.util.Date.class;
            case "salary", "balance" -> Double.class;
            default -> Object.class;
        };
    }

    /**
     * Convert JDBC value to target Java type.
     */
    private Object convertType(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType == Long.class || targetType == long.class) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } else if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } else if (targetType == Double.class || targetType == double.class) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue() != 0;
            }
            return Boolean.parseBoolean(value.toString());
        } else if (targetType == String.class) {
            return value.toString();
        } else if (targetType == java.util.Date.class) {
            if (value instanceof java.sql.Timestamp) {
                return new java.util.Date(((java.sql.Timestamp) value).getTime());
            } else if (value instanceof java.sql.Date) {
                return new java.util.Date(((java.sql.Date) value).getTime());
            }
        }

        return value;
    }

    private EntityMetadata getMetadata(Class<?> entityClass) {
        return metadataCache.computeIfAbsent(entityClass, metadataParser::parse);
    }

    /**
     * Result of parsing a constructor expression.
     */
    private static class ConstructorExpression {
        final String className;
        final List<String> paramNames;
        final List<Class<?>> paramTypes;

        ConstructorExpression(String className, List<String> paramNames, List<Class<?>> paramTypes) {
            this.className = className;
            this.paramNames = paramNames;
            this.paramTypes = paramTypes;
        }
    }
}
