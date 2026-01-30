package org.ltl.minihibernate.internal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.persistence.*;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Parameter;
import jakarta.persistence.TemporalType;

public class MiniNativeQueryImpl implements Query {

  protected final String sql;
  protected final Connection connection;
  protected final List<Object> parameters = new ArrayList<>();

  public MiniNativeQueryImpl(String sql, Connection connection) {
    this.sql = sql;
    this.connection = connection;
  }

  protected Connection getConnection() {
    return connection;
  }

  protected String getSql() {
    return sql;
  }

  protected String getJdbcSql() {
    // Replace ?1, ?2, etc. with ?
    return sql.replaceAll("\\?\\d+", "?");
  }


  @Override
  public List<Object[]> getResultList() {
    String jdbcSql = getJdbcSql();
    try (PreparedStatement stmt = connection.prepareStatement(jdbcSql)) {
      for (int i = 0; i < parameters.size(); i++) {
        stmt.setObject(i + 1, parameters.get(i));
      }
      try (ResultSet rs = stmt.executeQuery()) {
        List<Object[]> results = new ArrayList<>();
        int columnCount = rs.getMetaData().getColumnCount();
        while (rs.next()) {
          Object[] row = new Object[columnCount];
          for (int i = 1; i <= columnCount; i++) {
            row[i - 1] = rs.getObject(i);
          }
          results.add(row);
        }
        return results;
      }
    } catch (Exception e) {
      throw new RuntimeException("Native query failed", e);
    }
  }

  @Override
  public Object getSingleResult() {
    List<Object[]> results = getResultList();
    if (results.isEmpty())
      return null;
    return results.get(0);
  }

  @Override
  public int executeUpdate() {
    String jdbcSql = getJdbcSql();
    try (PreparedStatement stmt = connection.prepareStatement(jdbcSql)) {
      for (int i = 0; i < parameters.size(); i++) {
        stmt.setObject(i + 1, parameters.get(i));
      }
      return stmt.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException("Update failed", e);
    }
  }

  @Override
  public Query setParameter(int position, Object value) {
    while (parameters.size() < position)
      parameters.add(null);
    parameters.set(position - 1, value);
    return this;
  }

  @Override
  public Query setMaxResults(int maxResult) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getMaxResults() {
    return Integer.MAX_VALUE;
  }

  @Override
  public Query setFirstResult(int startPosition) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getFirstResult() {
    return 0;
  }

  @Override
  public Query setHint(String hintName, Object value) {
    return this;
  }

  @Override
  public Map<String, Object> getHints() {
    return java.util.Collections.emptyMap();
  }

  @Override
  public <T> Query setParameter(Parameter<T> param, T value) {
    if (param.getPosition() != null) {
      return setParameter(param.getPosition(), value);
    }
    throw new UnsupportedOperationException("Named parameters not supported in native queries");
  }

  @Override
  @SuppressWarnings("deprecation")
  public Query setParameter(Parameter<Calendar> param, Calendar value, TemporalType temporalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  @SuppressWarnings("deprecation")
  public Query setParameter(Parameter<Date> param, Date value, TemporalType temporalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Query setParameter(String name, Object value) {
    throw new UnsupportedOperationException("Named parameters not supported in native queries");
  }

  @Override
  @SuppressWarnings("deprecation")
  public Query setParameter(String name, Calendar value, TemporalType temporalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  @SuppressWarnings("deprecation")
  public Query setParameter(String name, Date value, TemporalType temporalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  @SuppressWarnings("deprecation")
  public Query setParameter(int position, Calendar value, TemporalType temporalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  @SuppressWarnings("deprecation")
  public Query setParameter(int position, Date value, TemporalType temporalType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<Parameter<?>> getParameters() {
    Set<Parameter<?>> result = new java.util.HashSet<>();
    
    // 1. Add parameters already set
    for (int i = 0; i < parameters.size(); i++) {
        result.add(getParameter(i + 1));
    }
    
    // 2. Scan SQL for positional parameters like ?1, ?2
    // This is a simple regex-based discovery for Spring Data JPA
    java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\?(\\d+)");
    java.util.regex.Matcher m = p.matcher(sql);
    while (m.find()) {
        int pos = Integer.parseInt(m.group(1));
        result.add(getParameter(pos));
    }
    
    return result;
  }

  @Override
  public Parameter<?> getParameter(String name) {
    return new Parameter<Object>() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public Integer getPosition() {
        return null;
      }

      @Override
      public Class<Object> getParameterType() {
        return Object.class;
      }
    };
  }

  @Override
  public <T> Parameter<T> getParameter(String name, Class<T> type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Parameter<?> getParameter(int position) {
    return new Parameter<Object>() {
      @Override
      public String getName() {
        return null;
      }

      @Override
      public Integer getPosition() {
        return position;
      }

      @Override
      public Class<Object> getParameterType() {
        return Object.class;
      }
    };
  }

  @Override
  public <T> Parameter<T> getParameter(int position, Class<T> type) {
    System.out.println("MiniNativeQueryImpl: getParameter(" + position + ", " + type + ")");
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isBound(Parameter<?> param) {
    Integer pos = param.getPosition();
    if (pos != null && pos > 0 && pos <= parameters.size()) {
      return parameters.get(pos - 1) != null;
    }
    return false;
  }

  @Override
  public <T> T getParameterValue(Parameter<T> param) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getParameterValue(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getParameterValue(int position) {
    return parameters.get(position - 1);
  }

  @Override
  public Query setFlushMode(FlushModeType flushMode) {
    return this;
  }

  @Override
  public FlushModeType getFlushMode() {
    return FlushModeType.AUTO;
  }

  @Override
  public Query setLockMode(LockModeType lockMode) {
    return this;
  }

  @Override
  public LockModeType getLockMode() {
    return LockModeType.NONE;
  }

  @Override
  public <T> T unwrap(Class<T> cls) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Query setTimeout(Integer timeoutMilliseconds) {
    return this;
  }

  @Override
  public Integer getTimeout() {
    return null;
  }

  @Override
  public Query setCacheStoreMode(CacheStoreMode cacheStoreMode) {
    return this;
  }

  @Override
  public CacheStoreMode getCacheStoreMode() {
    return CacheStoreMode.USE;
  }

  @Override
  public Query setCacheRetrieveMode(CacheRetrieveMode cacheRetrieveMode) {
    return this;
  }

  @Override
  public CacheRetrieveMode getCacheRetrieveMode() {
    return CacheRetrieveMode.USE;
  }

  @Override
  public Object getSingleResultOrNull() {
    return getSingleResult();
  }
}
