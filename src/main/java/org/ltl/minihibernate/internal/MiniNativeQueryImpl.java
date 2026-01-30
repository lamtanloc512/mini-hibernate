package org.ltl.minihibernate.internal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jakarta.persistence.Parameter;

public class MiniNativeQueryImpl<T> extends AbstractQuery<T> {

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
  public List<T> getResultList() {
    String jdbcSql = getJdbcSql();
    try (PreparedStatement stmt = connection.prepareStatement(jdbcSql)) {
      for (int i = 0; i < parameters.size(); i++) {
        stmt.setObject(i + 1, parameters.get(i));
      }
      try (ResultSet rs = stmt.executeQuery()) {
        List<T> results = new ArrayList<>();
        int columnCount = rs.getMetaData().getColumnCount();
        while (rs.next()) {
          Object[] row = new Object[columnCount];
          for (int i = 1; i <= columnCount; i++) {
            row[i - 1] = rs.getObject(i);
          }
          @SuppressWarnings("unchecked")
          T result = (T) row;
          results.add(result);
        }
        return results;
      }
    } catch (Exception e) {
      throw new RuntimeException("Native query failed", e);
    }
  }

  @Override
  public T getSingleResult() {
    List<T> results = getResultList();
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
  public MiniNativeQueryImpl<T> setParameter(int position, Object value) {
    while (parameters.size() < position)
      parameters.add(null);
    parameters.set(position - 1, value);
    return this;
  }

  @Override
  public <U> MiniNativeQueryImpl<T> setParameter(Parameter<U> param, U value) {
    if (param.getPosition() != null) {
      return setParameter(param.getPosition(), value);
    }
    throw new UnsupportedOperationException("Named parameters not supported in native queries");
  }

  @Override
  public Set<Parameter<?>> getParameters() {
    Set<Parameter<?>> result = new java.util.HashSet<>();
    
    // 1. Add parameters already set
    for (int i = 0; i < parameters.size(); i++) {
        result.add(getParameter(i + 1));
    }
    
    // 2. Scan SQL for positional parameters like ?1, ?2
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
  public boolean isBound(Parameter<?> param) {
    Integer pos = param.getPosition();
    if (pos != null && pos > 0 && pos <= parameters.size()) {
      return parameters.get(pos - 1) != null;
    }
    return false;
  }

  @Override
  public Object getParameterValue(int position) {
    return parameters.get(position - 1);
  }
}
