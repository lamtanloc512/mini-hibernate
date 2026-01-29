package com.minihibernate.repository;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Optional;

import com.minihibernate.annotation.Query;
import com.minihibernate.api.MiniEntityManager;
import com.minihibernate.api.MiniEntityManagerFactory;
import com.minihibernate.metadata.EntityMetadata;
import com.minihibernate.metadata.FieldMetadata;
import com.minihibernate.metadata.MetadataParser;

import io.vavr.collection.List;
import io.vavr.control.Try;

/**
 * Creates proxy instances for repository interfaces.
 * 
 * This is the MAGIC behind Spring Data JPA's @Query!
 * 
 * How it works:
 * 1. User defines interface: UserRepository extends MiniRepository<User, Long>
 * 2. We create a dynamic proxy that implements this interface
 * 3. When methods are called, we intercept them:
 * - If method has @Query → execute that SQL
 * - If method is from MiniRepository → execute default implementation
 */
public class RepositoryFactory {

  private final MiniEntityManagerFactory emFactory;
  private final MetadataParser metadataParser = new MetadataParser();

  public RepositoryFactory(MiniEntityManagerFactory emFactory) {
    this.emFactory = emFactory;
  }

  /**
   * Creates a repository proxy for the given interface.
   */
  @SuppressWarnings("unchecked")
  public <T extends MiniRepository<?, ?>> T createRepository(Class<T> repositoryInterface) {
    Class<?> entityClass = extractEntityClass(repositoryInterface);
    EntityMetadata metadata = metadataParser.parse(entityClass);

    return (T) Proxy.newProxyInstance(
        repositoryInterface.getClassLoader(),
        new Class<?>[] { repositoryInterface },
        new RepositoryInvocationHandler(metadata, entityClass));
  }

  private Class<?> extractEntityClass(Class<?> repositoryInterface) {
    return List.of(repositoryInterface.getGenericInterfaces())
        .filter(t -> t instanceof ParameterizedType)
        .map(t -> (ParameterizedType) t)
        .filter(pt -> pt.getRawType() == MiniRepository.class)
        .headOption()
        .map(pt -> (Class<?>) pt.getActualTypeArguments()[0])
        .getOrElseThrow(() -> new IllegalArgumentException(
            "Cannot extract entity class from " + repositoryInterface));
  }

  /**
   * InvocationHandler that intercepts all method calls on the repository.
   */
  private class RepositoryInvocationHandler implements InvocationHandler {

    private final EntityMetadata metadata;
    private final Class<?> entityClass;

    RepositoryInvocationHandler(EntityMetadata metadata, Class<?> entityClass) {
      this.metadata = metadata;
      this.entityClass = entityClass;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      Query queryAnnotation = method.getAnnotation(Query.class);

      if (queryAnnotation != null) {
        return executeQuery(queryAnnotation.value(), method, args);
      }

      String methodName = method.getName();

      if ("deleteById".equals(methodName)) {
        executeDeleteById(args[0]);
        return null;
      }

      return switch (methodName) {
        case "save" -> executeSave(args[0]);
        case "findById" -> executeFindById(args[0]);
        case "findAll" -> executeFindAll();
        case "existsById" -> executeExistsById(args[0]);
        case "count" -> executeCount();
        case "toString" -> entityClass.getSimpleName() + "Repository@proxy";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        default -> throw new UnsupportedOperationException("Method not implemented: " + methodName);
      };
    }

    /**
     * Executes a custom @Query using Vavr Try for clean error handling.
     */
    private Object executeQuery(String sql, Method method, Object[] args) {
      String processedSql = processQueryParams(sql, args);

      return Try.withResources(() -> emFactory.createEntityManager())
          .of(em -> executeAndMapQuery(em, processedSql, method, args))
          .getOrElseThrow(e -> new RuntimeException("Query execution failed", e));
    }

    private String processQueryParams(String sql, Object[] args) {
      String result = sql;
      for (int i = 1; i <= (args != null ? args.length : 0); i++) {
        result = result.replace("?" + i, "?");
      }
      return result;
    }

    private Object executeAndMapQuery(MiniEntityManager em, String sql, Method method, Object[] args)
        throws Exception {
      Connection conn = em.unwrap(Connection.class);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        setParameters(ps, args);

        try (ResultSet rs = ps.executeQuery()) {
          return mapResultsByReturnType(rs, method.getReturnType());
        }
      }
    }

    private void setParameters(PreparedStatement ps, Object[] args) throws Exception {
      if (args != null) {
        for (int i = 0; i < args.length; i++) {
          ps.setObject(i + 1, args[i]);
        }
      }
    }

    private Object mapResultsByReturnType(ResultSet rs, Class<?> returnType) throws Exception {
      // Return type is List
      if (java.util.List.class.isAssignableFrom(returnType)) {
        java.util.List<Object> results = new ArrayList<>();
        while (rs.next()) {
          results.add(mapResultSet(rs));
        }
        return results;
      }

      // Return type is Optional
      if (returnType == Optional.class) {
        return rs.next() ? Optional.of(mapResultSet(rs)) : Optional.empty();
      }

      // Return single entity
      return rs.next() ? mapResultSet(rs) : null;
    }

    private Object executeSave(Object entity) {
      return Try.withResources(() -> emFactory.createEntityManager())
          .of(em -> {
            em.getTransaction().begin();
            em.persist(entity);
            em.getTransaction().commit();
            return entity;
          })
          .getOrElseThrow(e -> new RuntimeException("Save failed", e));
    }

    private Optional<Object> executeFindById(Object id) {
      return Try.withResources(() -> emFactory.createEntityManager())
          .<Optional<Object>>of(em -> Optional.ofNullable(em.find(entityClass, id)))
          .getOrElseThrow(e -> new RuntimeException("FindById failed", e));
    }

    private java.util.List<Object> executeFindAll() {
      String sql = "SELECT * FROM " + metadata.getTableName();

      return Try.withResources(() -> emFactory.createEntityManager())
          .of(em -> {
            Connection conn = em.unwrap(Connection.class);
            java.util.List<Object> results = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
              while (rs.next()) {
                results.add(mapResultSet(rs));
              }
            }
            return results;
          })
          .getOrElseThrow(e -> new RuntimeException("FindAll failed", e));
    }

    private void executeDeleteById(Object id) {
      Try.withResources(() -> emFactory.createEntityManager())
          .of(em -> {
            em.getTransaction().begin();
            Object entity = em.find(entityClass, id);
            if (entity != null) {
              em.remove(entity);
            }
            em.getTransaction().commit();
            return null;
          })
          .getOrElseThrow(e -> new RuntimeException("DeleteById failed", e));
    }

    private boolean executeExistsById(Object id) {
      return executeFindById(id).isPresent();
    }

    private long executeCount() {
      String sql = "SELECT COUNT(*) FROM " + metadata.getTableName();

      return Try.withResources(() -> emFactory.createEntityManager())
          .of(em -> {
            Connection conn = em.unwrap(Connection.class);
            try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
              return rs.next() ? rs.getLong(1) : 0L;
            }
          })
          .getOrElseThrow(e -> new RuntimeException("Count failed", e));
    }

    private Object mapResultSet(ResultSet rs) throws Exception {
      Object entity = metadata.newInstance();

      for (FieldMetadata field : metadata.getAllColumns()) {
        Object value = rs.getObject(field.getColumnName());
        field.setValue(entity, convertType(value, field.getJavaType()));
      }

      return entity;
    }

    private Object convertType(Object value, Class<?> targetType) {
      if (value == null)
        return null;

      if (value instanceof Number num) {
        if (targetType == Long.class || targetType == long.class)
          return num.longValue();
        if (targetType == Integer.class || targetType == int.class)
          return num.intValue();
      }

      return value;
    }
  }
}
