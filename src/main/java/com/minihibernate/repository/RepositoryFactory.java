package com.minihibernate.repository;

import com.minihibernate.api.MiniEntityManager;
import com.minihibernate.api.MiniEntityManagerFactory;
import com.minihibernate.metadata.EntityMetadata;
import com.minihibernate.metadata.FieldMetadata;
import com.minihibernate.metadata.MetadataParser;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    // Extract entity class from generic type
    Class<?> entityClass = extractEntityClass(repositoryInterface);
    EntityMetadata metadata = metadataParser.parse(entityClass);

    return (T) Proxy.newProxyInstance(
        repositoryInterface.getClassLoader(),
        new Class<?>[] { repositoryInterface },
        new RepositoryInvocationHandler(metadata, entityClass));
  }

  private Class<?> extractEntityClass(Class<?> repositoryInterface) {
    for (Type type : repositoryInterface.getGenericInterfaces()) {
      if (type instanceof ParameterizedType pt) {
        if (pt.getRawType() == MiniRepository.class) {
          return (Class<?>) pt.getActualTypeArguments()[0];
        }
      }
    }
    throw new IllegalArgumentException("Cannot extract entity class from " + repositoryInterface);
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
      // Check if method has @Query annotation
      Query queryAnnotation = method.getAnnotation(Query.class);

      if (queryAnnotation != null) {
        return executeQuery(queryAnnotation.value(), method, args);
      }

      // Handle standard MiniRepository methods
      String methodName = method.getName();

      // Handle void methods separately
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
        case "toString" -> repositoryInterface().getSimpleName() + "@proxy";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        default -> throw new UnsupportedOperationException(
            "Method not implemented: " + method.getName());
      };
    }

    private Class<?> repositoryInterface() {
      return entityClass;
    }

    /**
     * Executes a custom @Query.
     */
    private Object executeQuery(String sql, Method method, Object[] args) throws Exception {
      // Replace ?1, ?2, etc. with actual ? placeholders
      String processedSql = sql;
      for (int i = 1; i <= (args != null ? args.length : 0); i++) {
        processedSql = processedSql.replace("?" + i, "?");
      }

      try (MiniEntityManager em = emFactory.createEntityManager()) {
        Connection conn = em.unwrap(Connection.class);

        try (PreparedStatement ps = conn.prepareStatement(processedSql)) {
          // Set parameters
          if (args != null) {
            for (int i = 0; i < args.length; i++) {
              ps.setObject(i + 1, args[i]);
            }
          }

          try (ResultSet rs = ps.executeQuery()) {
            Class<?> returnType = method.getReturnType();

            // Return type is List
            if (List.class.isAssignableFrom(returnType)) {
              List<Object> results = new ArrayList<>();
              while (rs.next()) {
                results.add(mapResultSet(rs));
              }
              return results;
            }

            // Return type is Optional
            if (returnType == Optional.class) {
              if (rs.next()) {
                return Optional.of(mapResultSet(rs));
              }
              return Optional.empty();
            }

            // Return single entity
            if (rs.next()) {
              return mapResultSet(rs);
            }
            return null;
          }
        }
      }
    }

    private Object executeSave(Object entity) throws Exception {
      try (MiniEntityManager em = emFactory.createEntityManager()) {
        em.getTransaction().begin();
        em.persist(entity);
        em.getTransaction().commit();
        return entity;
      }
    }

    private Optional<Object> executeFindById(Object id) throws Exception {
      try (MiniEntityManager em = emFactory.createEntityManager()) {
        Object result = em.find(entityClass, id);
        return Optional.ofNullable(result);
      }
    }

    private List<Object> executeFindAll() throws Exception {
      String sql = "SELECT * FROM " + metadata.getTableName();
      try (MiniEntityManager em = emFactory.createEntityManager()) {
        Connection conn = em.unwrap(Connection.class);
        List<Object> results = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            results.add(mapResultSet(rs));
          }
        }
        return results;
      }
    }

    private void executeDeleteById(Object id) throws Exception {
      try (MiniEntityManager em = emFactory.createEntityManager()) {
        em.getTransaction().begin();
        Object entity = em.find(entityClass, id);
        if (entity != null) {
          em.remove(entity);
        }
        em.getTransaction().commit();
      }
    }

    private boolean executeExistsById(Object id) throws Exception {
      return executeFindById(id).isPresent();
    }

    private long executeCount() throws Exception {
      String sql = "SELECT COUNT(*) FROM " + metadata.getTableName();
      try (MiniEntityManager em = emFactory.createEntityManager()) {
        Connection conn = em.unwrap(Connection.class);
        try (PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            return rs.getLong(1);
          }
        }
      }
      return 0;
    }

    private Object mapResultSet(ResultSet rs) throws Exception {
      Object entity = metadata.newInstance();

      for (FieldMetadata field : metadata.getAllColumns()) {
        String columnName = field.getColumnName();
        Object value = rs.getObject(columnName);
        value = convertType(value, field.getJavaType());
        field.setValue(entity, value);
      }

      return entity;
    }

    private Object convertType(Object value, Class<?> targetType) {
      if (value == null)
        return null;

      if (targetType == Long.class || targetType == long.class) {
        if (value instanceof Number)
          return ((Number) value).longValue();
      } else if (targetType == Integer.class || targetType == int.class) {
        if (value instanceof Number)
          return ((Number) value).intValue();
      }

      return value;
    }
  }
}
