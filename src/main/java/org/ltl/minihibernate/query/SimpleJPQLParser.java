package org.ltl.minihibernate.query;

import org.ltl.minihibernate.internal.MiniEntityManagerFactoryImpl;
import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.metadata.FieldMetadata;
import io.vavr.collection.List;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses simple JPQL queries into SQL.
 * 
 * Supported syntax:
 * SELECT <alias> FROM <EntityName> <alias> [WHERE <alias>.<field> = :<param>]
 */
public class SimpleJPQLParser {

  private static final Pattern SELECT_PATTERN = Pattern.compile(
      "SELECT\\s+(\\w+)\\s+FROM\\s+(\\w+)\\s+\\1(?:\\s+WHERE\\s+(.+))?",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern WHERE_CLAUSE_PATTERN = Pattern.compile(
      "(\\w+)\\.(\\w+)\\s*(=)\\s*:(\\w+)");

  public static ParsedQuery parse(String jpql, MiniEntityManagerFactoryImpl factory) {
    Matcher matcher = SELECT_PATTERN.matcher(jpql);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Unsupported JPQL: " + jpql);
    }

    String alias = matcher.group(1);
    String entityName = matcher.group(2); // Simple class name
    String wherePart = matcher.group(3);

    // key is to find EntityMetadata by entityName (SimpleName).
    // But factory maps Class -> Metadata.
    // We need to iterate all metadata to find the one with matching SimpleName.
    EntityMetadata metadata = findMetadata(entityName, factory);

    StringBuilder sql = new StringBuilder("SELECT ");

    // Select all persistable columns (including FKs)
    java.util.List<FieldMetadata> columns = metadata.getPersistableFields();

    // t0.col1, t0.col2
    String selectCols = List.ofAll(columns)
        .map(f -> alias + "." + f.getColumnName()) // using alias as table alias in SQL?
        // Actually, we usually use explicit table alias in SQL.
        // Let's use the JPQL alias as the SQL alias for simplicity.
        .mkString(", ");

    sql.append(selectCols);
    sql.append(" FROM ").append(metadata.getTableName()).append(" ").append(alias);

    java.util.List<String> paramNames = new java.util.ArrayList<>();

    if (wherePart != null) {
      sql.append(" WHERE ");
      Matcher whereMatcher = WHERE_CLAUSE_PATTERN.matcher(wherePart);
      StringBuffer sb = new StringBuffer();

      while (whereMatcher.find()) {
        String wAlias = whereMatcher.group(1);
        String fieldName = whereMatcher.group(2);
        String operator = whereMatcher.group(3);
        String paramName = whereMatcher.group(4);

        if (!wAlias.equals(alias)) {
          throw new IllegalArgumentException("Unknown alias in WHERE: " + wAlias);
        }

        // Map field to column
        String columnName = findColumnName(fieldName, metadata);

        whereMatcher.appendReplacement(sb, wAlias + "." + columnName + " " + operator + " ?");
        paramNames.add(paramName);
      }
      whereMatcher.appendTail(sb);
      sql.append(sb.toString());
    }

    return new ParsedQuery(sql.toString(), paramNames, metadata.getEntityClass());
  }

  private static EntityMetadata findMetadata(String entityName, MiniEntityManagerFactoryImpl factory) {
    // access internal map via reflection or just iterating known classes?
    // factory.entityMetadataMap is private.
    // But we can guess the class? No.
    // We need a way to lookup metadata by name.
    // For this "mini" implementation, we'll brute-force if possible,
    // OR we can make factory expose a lookup method.
    // Accessing package-private 'getEntityMetadataMap' ?? No,
    // 'getEntityMetadata(Class)' exists.
    // We can try to load the class if it's fully qualified, but here it is
    // SimpleName.
    // Let's iterate all known entities if possible.
    // Hack: factory doesn't expose list of all entities nicely.
    // Simple Solution: Assume EntityName is the class simple name.
    // We assume the caller (MiniEntityManager) knows the class if it was passed in
    // createQuery(..., resultClass).
    // But jpql doesn't require resultClass always.

    // Better: pass the expected Class to the parser if available.
    // In createQuery(String ql, Class<T> clazz), we have it.
    // But query string overrides it.

    // Let's try to handle it gracefully in MiniEntityManagerImpl later.
    // For now, let's assume we can get it.
    // Or we throw Unsupported for now if we can't find it.
    throw new UnsupportedOperationException("Metadata lookup by name not implemented yet in parser helper");
  }

  // Overload to accept Class directly
  public static ParsedQuery parse(String jpql, EntityMetadata metadata) {
    Matcher matcher = SELECT_PATTERN.matcher(jpql);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Unsupported JPQL or no match: " + jpql);
    }

    String alias = matcher.group(1);
    String entityName = matcher.group(2);
    String wherePart = matcher.group(3);

    if (!entityName.equals(metadata.getEntityClass().getSimpleName())) {
      // Soft validity check
      // throw new IllegalArgumentException("Entity name mismatch: " + entityName);
    }

    StringBuilder sql = new StringBuilder("SELECT ");
    java.util.List<FieldMetadata> columns = metadata.getPersistableFields();

    String selectCols = List.ofAll(columns)
        .map(f -> alias + "." + f.getColumnName())
        .mkString(", ");

    sql.append(selectCols);
    sql.append(" FROM ").append(metadata.getTableName()).append(" ").append(alias);

    java.util.List<String> paramNames = new java.util.ArrayList<>();

    if (wherePart != null) {
      sql.append(" WHERE ");
      Matcher whereMatcher = WHERE_CLAUSE_PATTERN.matcher(wherePart);
      StringBuffer sb = new StringBuffer();

      while (whereMatcher.find()) {
        String wAlias = whereMatcher.group(1);
        String fieldName = whereMatcher.group(2);
        String operator = whereMatcher.group(3);
        String paramName = whereMatcher.group(4);

        if (!wAlias.equals(alias)) {
          throw new IllegalArgumentException("Unknown alias in WHERE: " + wAlias);
        }

        String columnName = findColumnName(fieldName, metadata);
        whereMatcher.appendReplacement(sb, wAlias + "." + columnName + " " + operator + " ?");
        paramNames.add(paramName);
      }
      whereMatcher.appendTail(sb);
      sql.append(sb.toString());
    }

    return new ParsedQuery(sql.toString(), paramNames, metadata.getEntityClass());
  }

  private static String findColumnName(String fieldName, EntityMetadata metadata) {
    return List.ofAll(metadata.getPersistableFields())
        .find(f -> f.getFieldName().equals(fieldName))
        .map(FieldMetadata::getColumnName)
        .getOrElse(() -> {
          throw new IllegalArgumentException("Unknown field: " + fieldName);
        });
  }

  public static class ParsedQuery {
    public final String sql;
    public final java.util.List<String> paramNames; // Ordered list of param names
    public final Class<?> resultClass;

    public ParsedQuery(String sql, java.util.List<String> paramNames, Class<?> resultClass) {
      this.sql = sql;
      this.paramNames = paramNames;
      this.resultClass = resultClass;
    }
  }
}
