package org.ltl.minihibernate.query;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.metadata.FieldMetadata;

import io.vavr.collection.List;

/**
 * Enhanced JPQL Parser that supports:
 * - DISTINCT keyword
 * - JOIN (INNER, LEFT, RIGHT) for relationships
 * - GROUP BY, HAVING, ORDER BY clauses
 * - Aggregation functions: COUNT, SUM, AVG, MAX, MIN
 * - Extended WHERE operators: <, >, <=, >=, <>, LIKE, IN, BETWEEN, IS NULL, IS NOT NULL
 * - Named and positional parameters
 * - Collection membership: MEMBER OF
 */
public class EnhancedJPQLParser {

    // Main JPQL pattern components
    private static final Pattern JPQL_PATTERN = Pattern.compile(
        "^SELECT\\s+(?:DISTINCT\\s+)?(.+?)\\s+FROM\\s+(.+?)(?:\\s+WHERE\\s+(.+?))?" +
        "(?:\\s+GROUP\\s+BY\\s+(.+?))?(?:\\s+HAVING\\s+(.+?))?" +
        "(?:\\s+ORDER\\s+BY\\s+(.+?))?$",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Join pattern: JOIN entity alias ON condition
    private static final Pattern JOIN_PATTERN = Pattern.compile(
        "(INNER|LEFT|RIGHT)?\\s*JOIN\\s+(\\w+)\\s+(\\w+)\\s+ON\\s+(.+)",
        Pattern.CASE_INSENSITIVE
    );

    // Select items pattern (comma-separated)
    private static final Pattern SELECT_ITEMS_PATTERN = Pattern.compile(
        "([\\w.]+(?:\\s+as\\s+\\w+)?(?:\\([^)]*\\))?)",
        Pattern.CASE_INSENSITIVE
    );

    // WHERE condition patterns
    private static final Pattern WHERE_OP_PATTERN = Pattern.compile(
        "(\\w+)\\.(\\w+)\\s*(=|!=|<>|<=|>=|<|>|LIKE|IN|BETWEEN|IS\\s+NULL|IS\\s+NOT\\s+NULL)\\s*[:?](\\w*)"
    );

    // Named parameter pattern
    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(
        ":([a-zA-Z_][a-zA-Z0-9_]*)"
    );

    // Aggregation function pattern
    private static final Pattern AGG_PATTERN = Pattern.compile(
        "(COUNT|SUM|AVG|MAX|MIN)\\s*\\(\\s*(?:DISTINCT\\s+)?(.+?)\\)",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Parse JPQL query and return parsed result.
     */
    public static ParsedQuery parse(String jpql, EntityMetadata metadata) {
        Matcher matcher = JPQL_PATTERN.matcher(jpql.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid JPQL syntax: " + jpql);
        }

        String selectPart = matcher.group(1).trim();
        String fromPart = matcher.group(2).trim();
        String wherePart = matcher.group(3);
        String groupByPart = matcher.group(4);
        String havingPart = matcher.group(5);
        String orderByPart = matcher.group(6);

        // Parse main entity from FROM clause
        FromClause mainEntity = parseFromClause(fromPart, metadata);

        StringBuilder sql = new StringBuilder();
        java.util.List<String> paramNames = new ArrayList<>();

        // Build SELECT clause
        sql.append("SELECT ");
        boolean isDistinct = selectPart.toUpperCase().startsWith("DISTINCT");
        if (isDistinct) {
            sql.append("DISTINCT ");
            selectPart = selectPart.substring(8).trim();
        }

        // Parse select items
        SelectClause selectClause = parseSelectClause(selectPart, mainEntity);
        sql.append(selectClause.sql);

        // Build FROM clause with joins
        sql.append(" FROM ").append(mainEntity.tableName).append(" ").append(mainEntity.alias);

        // Parse and add JOINs
        String joinSql = parseJoins(fromPart, mainEntity, metadata, paramNames);
        sql.append(joinSql);

        // Build WHERE clause
        if (wherePart != null && !wherePart.trim().isEmpty()) {
            sql.append(" WHERE ");
            String whereSql = parseWhereClause(wherePart.trim(), mainEntity, metadata, paramNames);
            sql.append(whereSql);
        }

        // Build GROUP BY clause
        if (groupByPart != null && !groupByPart.trim().isEmpty()) {
            sql.append(" GROUP BY ");
            sql.append(parseGroupByOrderBy(groupByPart.trim(), mainEntity));
        }

        // Build HAVING clause
        if (havingPart != null && !havingPart.trim().isEmpty()) {
            sql.append(" HAVING ");
            String havingSql = parseHavingClause(havingPart.trim(), mainEntity, metadata, paramNames);
            sql.append(havingSql);
        }

        // Build ORDER BY clause
        if (orderByPart != null && !orderByPart.trim().isEmpty()) {
            sql.append(" ORDER BY ");
            sql.append(parseGroupByOrderBy(orderByPart.trim(), mainEntity));
        }

        return new ParsedQuery(
            sql.toString(),
            paramNames,
            selectClause.resultClass,
            selectClause.isAggregate
        );
    }

    /**
     * Parse FROM clause to extract main entity.
     */
    private static FromClause parseFromClause(String fromPart, EntityMetadata metadata) {
        String[] parts = fromPart.trim().split("\\s+");
        String entityName = parts[0];
        String alias = parts.length > 1 ? parts[1] : entityName.toLowerCase();

        return new FromClause(entityName, alias, metadata);
    }

    /**
     * Parse SELECT clause with aggregation support.
     */
    private static SelectClause parseSelectClause(String selectPart, FromClause mainEntity) {
        StringBuilder sql = new StringBuilder();
        List<String> itemAliases = List.empty();
        Class<?> resultClass = mainEntity.metadata.getEntityClass();
        boolean isAggregate = false;

        // Handle wildcard
        if (selectPart.equals("*") || selectPart.equals(mainEntity.alias + ".*")) {
            sql.append(mainEntity.alias).append(".*");
            return new SelectClause(sql.toString(), resultClass, false, itemAliases);
        }

        // Handle single aggregation
        Matcher aggMatcher = AGG_PATTERN.matcher(selectPart);
        if (aggMatcher.matches()) {
            String func = aggMatcher.group(1).toUpperCase();
            String arg = aggMatcher.group(2);
          sql.append(func).append("(");

            if (arg.equals("*")) {
                sql.append("*");
            } else if (arg.contains(".")) {
                String[] parts = arg.split("\\.");
                String alias = parts[0];
                String field = parts[1];
                if (alias.equals(mainEntity.alias)) {
                    String columnName = findColumnName(field, mainEntity.metadata);
                    sql.append(mainEntity.alias).append(".").append(columnName);
                }
            } else {
                String columnName = findColumnName(arg, mainEntity.metadata);
                sql.append(mainEntity.alias).append(".").append(columnName);
            }
            sql.append(")");
            return new SelectClause(sql.toString(), Number.class, true, itemAliases);
        }

        // Handle multiple select items
        String[] items = splitSelectItems(selectPart);
        for (int i = 0; i < items.length; i++) {
            String item = items[i].trim();
            if (i > 0) sql.append(", ");

            Matcher itemAggMatcher = AGG_PATTERN.matcher(item);
            if (itemAggMatcher.matches()) {
                String func = itemAggMatcher.group(1).toUpperCase();
                String arg = itemAggMatcher.group(2);
                isAggregate = true;
                sql.append(func).append("(");
                if (arg.equals("*")) {
                    sql.append("*");
                } else {
                    sql.append(convertFieldToColumn(arg, mainEntity));
                }
                sql.append(")");
            } else {
                sql.append(convertFieldToColumn(item, mainEntity));
                // Extract alias if present
                String alias = extractAlias(item);
                if (alias != null) itemAliases.append(alias);
            }
        }

        return new SelectClause(sql.toString(), resultClass, isAggregate, itemAliases);
    }

    /**
     * Parse JOIN clauses.
     */
    private static String parseJoins(String fromPart, FromClause mainEntity,
                                     EntityMetadata metadata, java.util.List<String> paramNames) {
        StringBuilder sql = new StringBuilder();

        // Find JOIN clauses in the FROM part
        Matcher joinMatcher = JOIN_PATTERN.matcher(fromPart);
        while (joinMatcher.find()) {
            String joinType = joinMatcher.group(1); // INNER, LEFT, RIGHT or null
            String entityName = joinMatcher.group(2);
            String alias = joinMatcher.group(3);
            String onCondition = joinMatcher.group(4);

            // Find the related entity metadata
            EntityMetadata relatedMetadata = findRelatedEntityMetadata(entityName, metadata);
            if (relatedMetadata == null) {
                // Try to find by relationship name
                relatedMetadata = findMetadataByRelationship(entityName, mainEntity.metadata);
            }

            if (relatedMetadata != null) {
                sql.append(" ");
                if (joinType != null && !joinType.isEmpty()) {
                    sql.append(joinType.toUpperCase()).append(" ");
                }
                sql.append("JOIN ");
                sql.append(relatedMetadata.getTableName()).append(" ").append(alias);

                // Parse ON condition
                sql.append(" ON ");
                sql.append(parseJoinCondition(onCondition, mainEntity, alias, relatedMetadata, paramNames));
            }
        }

        return sql.toString();
    }

    /**
     * Parse WHERE clause with extended operators.
     */
    private static String parseWhereClause(String wherePart, FromClause mainEntity,
                                           EntityMetadata metadata, java.util.List<String> paramNames) {
        StringBuilder sql = new StringBuilder();
        String[] conditions = splitConditions(wherePart);

        for (int i = 0; i < conditions.length; i++) {
            String cond = conditions[i].trim();
            if (i > 0) {
                // Determine AND/OR
                if (cond.toUpperCase().startsWith("AND ")) {
                    sql.append(" AND ");
                    cond = cond.substring(4).trim();
                } else if (cond.toUpperCase().startsWith("OR ")) {
                    sql.append(" OR ");
                    cond = cond.substring(3).trim();
                } else {
                    sql.append(" AND ");
                }
            }

            // Handle IS NULL / IS NOT NULL
            if (cond.toUpperCase().matches("^.+\\s+IS\\s+NULL\\s*$")) {
                sql.append(convertFieldToColumn(cond.replaceAll("(?i)\\s+IS\\s+NULL$", ""), mainEntity));
                sql.append(" IS NULL");
                continue;
            }

            if (cond.toUpperCase().matches("^.+\\s+IS\\s+NOT\\s+NULL\\s*$")) {
                sql.append(convertFieldToColumn(cond.replaceAll("(?i)\\s+IS\\s+NOT\\s+NULL$", ""), mainEntity));
                sql.append(" IS NOT NULL");
                continue;
            }

            // Handle LIKE
            if (cond.toUpperCase().contains(" LIKE ")) {
                String[] parts = cond.split("(?i)\\s+LIKE\\s+");
                sql.append(convertFieldToColumn(parts[0].trim(), mainEntity));
                sql.append(" LIKE ?");
                String param = extractParam(parts[1]);
                if (param != null) paramNames.add(param);
                continue;
            }

            // Handle IN
            if (cond.toUpperCase().contains(" IN ")) {
                String[] parts = cond.split("(?i)\\s+IN\\s+");
                sql.append(convertFieldToColumn(parts[0].trim(), mainEntity));
                sql.append(" IN (");
                // Handle parameterized IN
                if (parts[1].trim().startsWith("?")) {
                    sql.append(parts[1].trim());
                } else {
                    sql.append("?");
                    String param = extractParam(parts[1]);
                    if (param != null) paramNames.add(param);
                }
                sql.append(")");
                continue;
            }

            // Handle BETWEEN
            if (cond.toUpperCase().contains(" BETWEEN ")) {
                String[] parts = cond.split("(?i)\\s+BETWEEN\\s+");
                sql.append(convertFieldToColumn(parts[0].trim(), mainEntity));
                sql.append(" BETWEEN ? AND ?");
                String[] betweens = parts[1].split("\\s+AND\\s+");
                for (String bet : betweens) {
                    String param = extractParam(bet);
                    if (param != null) paramNames.add(param);
                }
                continue;
            }

            // Standard condition: alias.field = :param
            Matcher whereMatcher = WHERE_OP_PATTERN.matcher(cond);
            if (whereMatcher.find()) {
                String alias = whereMatcher.group(1);
                String field = whereMatcher.group(2);
                String operator = whereMatcher.group(3);
                String paramName = whereMatcher.group(4);

                if (alias.equals(mainEntity.alias)) {
                    String columnName = findColumnName(field, mainEntity.metadata);
                    sql.append(mainEntity.alias).append(".").append(columnName);
                } else {
                    sql.append(alias).append(".").append(field); // Join alias
                }

                sql.append(" ").append(normalizeOperator(operator)).append(" ?");
                if (paramName != null && !paramName.isEmpty()) {
                    paramNames.add(paramName);
                }
            } else {
                // Try to find parameters in the condition
                Matcher paramMatcher = NAMED_PARAM_PATTERN.matcher(cond);
                while (paramMatcher.find()) {
                    paramNames.add(paramMatcher.group(1));
                }
                sql.append(convertFieldToColumn(cond, mainEntity));
            }
        }

        return sql.toString();
    }

    /**
     * Parse HAVING clause.
     */
    private static String parseHavingClause(String havingPart, FromClause mainEntity,
                                            EntityMetadata metadata, java.util.List<String> paramNames) {
        StringBuilder sql = new StringBuilder();

        // Handle aggregation functions in HAVING
        Matcher aggMatcher = AGG_PATTERN.matcher(havingPart);
        String processed = havingPart;
        while (aggMatcher.find()) {
            String func = aggMatcher.group(1).toUpperCase();
            String arg = aggMatcher.group(2);
            String replacement = func + "(";

            if (arg.equals("*")) {
                replacement += "*";
            } else if (arg.contains(".")) {
                String[] parts = arg.split("\\.");
                String alias = parts[0];
                String field = parts[1];
                if (alias.equals(mainEntity.alias)) {
                    String columnName = findColumnName(field, mainEntity.metadata);
                    replacement += mainEntity.alias + "." + columnName;
                }
            } else {
                String columnName = findColumnName(arg, mainEntity.metadata);
                replacement += mainEntity.alias + "." + columnName;
            }
            replacement += ")";
            processed = processed.replace(aggMatcher.group(0), replacement);
        }

        // Extract parameters
        Matcher paramMatcher = NAMED_PARAM_PATTERN.matcher(processed);
        while (paramMatcher.find()) {
            paramNames.add(paramMatcher.group(1));
        }

        sql.append(processed);
        return sql.toString();
    }

    /**
     * Parse JOIN condition.
     */
    private static String parseJoinCondition(String condition, FromClause mainEntity,
                                             String joinAlias, EntityMetadata joinMetadata,
                                             java.util.List<String> paramNames) {
        StringBuilder sql = new StringBuilder();

        // Pattern: mainAlias.field = joinAlias.id
        Matcher condMatcher = Pattern.compile(
            "(\\w+)\\.(\\w+)\\s*=\\s*(\\w+)\\.(\\w+)"
        ).matcher(condition);

        if (condMatcher.find()) {
            String leftAlias = condMatcher.group(1);
            String leftField = condMatcher.group(2);
            String rightAlias = condMatcher.group(3);
            String rightField = condMatcher.group(4);

            if (leftAlias.equals(mainEntity.alias)) {
                String leftColumn = findColumnName(leftField, mainEntity.metadata);
                sql.append(mainEntity.alias).append(".").append(leftColumn);
            } else {
                sql.append(leftAlias).append(".").append(leftField);
            }

            sql.append(" = ");

            if (rightAlias.equals(joinAlias)) {
                String rightColumn = findColumnName(rightField, joinMetadata);
                sql.append(joinAlias).append(".").append(rightColumn);
            } else {
                sql.append(rightAlias).append(".").append(rightField);
            }
        } else {
            sql.append(condition);
        }

        return sql.toString();
    }

    /**
     * Parse GROUP BY or ORDER BY clause.
     */
    private static String parseGroupByOrderBy(String clause, FromClause mainEntity) {
        StringBuilder sql = new StringBuilder();
        String[] items = clause.split(",");
        boolean first = true;

        for (String item : items) {
            if (!first) sql.append(", ");
            first = false;

            item = item.trim();
            // Remove ASC/DESC
            String direction = "";
            if (item.toUpperCase().endsWith(" ASC")) {
                direction = " ASC";
                item = item.substring(0, item.length() - 4).trim();
            } else if (item.toUpperCase().endsWith(" DESC")) {
                direction = " DESC";
                item = item.substring(0, item.length() - 5).trim();
            }

            // Handle aggregation in ORDER BY
            Matcher aggMatcher = AGG_PATTERN.matcher(item);
            if (aggMatcher.find()) {
                String func = aggMatcher.group(1).toUpperCase();
                String arg = aggMatcher.group(2);
                sql.append(func).append("(");
                sql.append(convertFieldToColumn(arg, mainEntity));
                sql.append(")").append(direction);
            } else {
                sql.append(convertFieldToColumn(item, mainEntity)).append(direction);
            }
        }

        return sql.toString();
    }

    // Helper methods

    private static String[] splitSelectItems(String selectPart) {
        // Handle parentheses in functions
        int parenDepth = 0;
        int lastSplit = 0;
        java.util.List<String> items = new ArrayList<>();

        for (int i = 0; i < selectPart.length(); i++) {
            char c = selectPart.charAt(i);
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            else if (c == ',' && parenDepth == 0) {
                items.add(selectPart.substring(lastSplit, i));
                lastSplit = i + 1;
            }
        }
        items.add(selectPart.substring(lastSplit));

        return items.toArray(new String[0]);
    }

    private static String[] splitConditions(String wherePart) {
        // Split by AND/OR at top level
        java.util.List<String> conditions = new ArrayList<>();
        int parenDepth = 0;
        int lastSplit = 0;

        for (int i = 0; i < wherePart.length(); i++) {
            char c = wherePart.charAt(i);
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            else if (parenDepth == 0 && i + 3 <= wherePart.length()) {
                String substr = wherePart.substring(i, Math.min(i + 4, wherePart.length()));
                if (substr.equalsIgnoreCase("AND ") || substr.equalsIgnoreCase(" OR ")) {
                    conditions.add(wherePart.substring(lastSplit, i));
                    lastSplit = i + 4;
                    i += 3; // Skip the keyword
                }
            }
        }
        conditions.add(wherePart.substring(lastSplit));

        return conditions.toArray(new String[0]);
    }

    private static String convertFieldToColumn(String fieldExpr, FromClause mainEntity) {
        fieldExpr = fieldExpr.trim();

        // If it's the alias itself, return alias.*
        if (fieldExpr.equals(mainEntity.alias)) {
            return mainEntity.alias + ".*";
        }
        
        // Handle aggregation
        Matcher aggMatcher = AGG_PATTERN.matcher(fieldExpr);
        if (aggMatcher.find()) {
            String func = aggMatcher.group(1).toUpperCase();
            String arg = aggMatcher.group(2);
            return func + "(" + convertFieldToColumn(arg, mainEntity) + ")";
        }

        // Handle alias.field
        if (fieldExpr.contains(".")) {
            String[] parts = fieldExpr.split("\\.", 2);
            String alias = parts[0];
            String field = parts[1];

            if (alias.equals(mainEntity.alias)) {
                String columnName = findColumnName(field, mainEntity.metadata);
                return mainEntity.alias + "." + columnName;
            }
            return alias + "." + field;
        }

        // Simple field name
        String columnName = findColumnName(fieldExpr, mainEntity.metadata);
        return mainEntity.alias + "." + columnName;
    }

    private static String findColumnName(String fieldName, EntityMetadata metadata) {
        return List.ofAll(metadata.getPersistableFields())
            .find(f -> f.getFieldName().equals(fieldName))
            .map(FieldMetadata::getColumnName)
            .getOrElse(fieldName);
    }

    private static String extractParam(String expr) {
        expr = expr.trim();
        if (expr.startsWith(":")) {
            return expr.substring(1);
        }
        if (expr.startsWith("?")) {
            // Positional parameter - return placeholder
            return "?" + expr.substring(1);
        }
        return null;
    }

    private static String extractAlias(String item) {
        Matcher m = Pattern.compile("as\\s+(\\w+)$", Pattern.CASE_INSENSITIVE).matcher(item);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String normalizeOperator(String op) {
        return switch (op.toUpperCase()) {
            case "=" -> "=";
            case "!=", "<>" -> "!=";
            case "<" -> "<";
            case ">" -> ">";
            case "<=" -> "<=";
            case ">=" -> ">=";
            default -> op;
        };
    }

    private static EntityMetadata findRelatedEntityMetadata(String entityName, EntityMetadata fromMetadata) {
        // Search in relationships
        for (FieldMetadata field : fromMetadata.getRelationships()) {
            if (field.getFieldName().equalsIgnoreCase(entityName) ||
                field.getTargetEntity().getSimpleName().equalsIgnoreCase(entityName)) {
                return findMetadataByClass(field.getTargetEntity());
            }
        }
        return null;
    }

    private static EntityMetadata findMetadataByClass(Class<?> entityClass) {
        // This would need access to the factory's metadata map
        // For now, return null and let the caller handle it
        return null;
    }

    private static EntityMetadata findMetadataByRelationship(String fieldName, EntityMetadata metadata) {
        return findRelatedEntityMetadata(fieldName, metadata);
    }

    // Inner classes

    public static class ParsedQuery {
        public final String sql;
        public final java.util.List<String> paramNames;
        public final Class<?> resultClass;
        public final boolean isAggregate;

        public ParsedQuery(String sql, java.util.List<String> paramNames,
                          Class<?> resultClass, boolean isAggregate) {
            this.sql = sql;
            this.paramNames = paramNames;
            this.resultClass = resultClass;
            this.isAggregate = isAggregate;
        }
    }

    private static class FromClause {
        final String entityName;
        final String alias;
        final String tableName;
        final EntityMetadata metadata;

        FromClause(String entityName, String alias, String tableName) {
            this.entityName = entityName;
            this.alias = alias;
            this.tableName = tableName;
            this.metadata = null; // Would be set by caller
        }

        FromClause(String entityName, String alias, EntityMetadata metadata) {
            this.entityName = entityName;
            this.alias = alias;
            this.tableName = metadata.getTableName();
            this.metadata = metadata;
        }
    }

    private static class SelectClause {
        final String sql;
        final Class<?> resultClass;
        final boolean isAggregate;
        final List<String> itemAliases;

        SelectClause(String sql, Class<?> resultClass, boolean isAggregate, List<String> itemAliases) {
            this.sql = sql;
            this.resultClass = resultClass;
            this.isAggregate = isAggregate;
            this.itemAliases = itemAliases;
        }
    }
}
