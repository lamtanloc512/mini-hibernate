# Mini-Hibernate: JPQL, Native Query & Lazy Loading

This document describes the advanced features implemented in mini-hibernate.

## 1. Enhanced JPQL Parser

The enhanced JPQL parser supports a wide range of SQL-like operations.

### 1.1 Supported Syntax

```java
// Basic SELECT
SELECT u FROM User u

// DISTINCT
SELECT DISTINCT u FROM User u

// WHERE with operators
SELECT u FROM User u WHERE u.name = :name
SELECT u FROM User u WHERE u.age > :minAge
SELECT u FROM User u WHERE u.age <= :maxAge
SELECT u FROM User u WHERE u.age <> :excludedAge

// LIKE
SELECT u FROM User u WHERE u.name LIKE :pattern

// IS NULL / IS NOT NULL
SELECT u FROM User u WHERE u.email IS NULL
SELECT u FROM User u WHERE u.email IS NOT NULL

// BETWEEN
SELECT u FROM User u WHERE u.age BETWEEN :min AND :max

// IN
SELECT u FROM User u WHERE u.email IN :emails

// Multiple conditions with AND/OR
SELECT u FROM User u WHERE u.name = :name AND u.age > :age

// ORDER BY
SELECT u FROM User u ORDER BY u.name ASC
SELECT u FROM User u ORDER BY u.age DESC

// GROUP BY
SELECT u FROM User u GROUP BY u.email

// HAVING
SELECT u FROM User u GROUP BY u.email HAVING u.age > :minAge

// Aggregations
SELECT COUNT(u) FROM User u
SELECT SUM(u.age) FROM User u
SELECT AVG(u.age) FROM User u
SELECT MAX(u.age) FROM User u
SELECT MIN(u.age) FROM User u
SELECT COUNT(*) FROM User u

// JOINs
SELECT o FROM Order o JOIN o.user u ON o.user_id = u.id
SELECT o FROM Order o LEFT JOIN o.user u ON o.user_id = u.id
SELECT o FROM Order o INNER JOIN o.user u ON o.user_id = u.id
```

### 1.2 Usage Example

```java
// Create typed query with enhanced JPQL
TypedQuery<User> query = em.createQuery(
    "SELECT u FROM User u WHERE u.age > :minAge ORDER BY u.name",
    User.class
);
query.setParameter("minAge", 18);
List<User> users = query.getResultList();
```

---

## 2. Native Query Mapping

Mini-hibernate supports mapping native SQL queries to entities and DTOs.

### 2.1 Entity Mapping

```java
@Query("SELECT * FROM users WHERE email = ?1")
@ResultEntity(User.class)
List<User> findByEmail(String email);
```

### 2.2 DTO/Projection Mapping with Constructor Expressions

```java
// DTO class
public class UserDTO {
    private final Long id;
    private final String name;
    
    public UserDTO(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}

// Repository method using constructor expression
@Query("SELECT new com.example.UserDTO(u.id, u.name) FROM User u WHERE u.age > :age")
List<UserDTO> findUserDTOs(int age);
```

### 2.3 TypedNativeQueryImpl Usage

```java
// Create typed native query with entity mapping
TypedNativeQueryImpl<User> query = new TypedNativeQueryImpl<>(
    "SELECT * FROM users WHERE email = ?",
    connection,
    User.class,
    entityManager
);
query.setParameter(1, "test@example.com");
query.setEntityClass(User.class);
List<User> users = query.getEntityResultList();

// DTO mapping
TypedNativeQueryImpl<UserDTO> dtoQuery = new TypedNativeQueryImpl<>(
    "SELECT new com.example.UserDTO(u.id, u.name) FROM users u",
    connection,
    UserDTO.class,
    entityManager
);
dtoQuery.setConstructorExpression("new com.example.UserDTO(u.id, u.name)");
List<UserDTO> dtos = dtoQuery.getResultList();
```

### 2.4 NativeQueryMapper

The `NativeQueryMapper` class handles mapping ResultSet to entities and DTOs:

```java
NativeQueryMapper mapper = new NativeQueryMapper(metadataParser);

// Map to entity
User user = mapper.mapToEntity(rs, User.class);

// Map to DTO with constructor expression
UserDTO dto = mapper.mapToDTO(rs, "new com.example.UserDTO(u.id, u.name)", UserDTO.class);

// Map to Map (scalar results)
Map<String, Object> row = mapper.mapToMap(rs, new HashMap<>());
```

---

## 3. Lazy Loading

Mini-hibernate supports lazy loading for entity relationships using Java Dynamic Proxy.

### 3.1 Defining Lazy Relationships

```java
@Entity
public class Order {
    @Id
    private Long id;
    
    // Lazy loading - will not be loaded until accessed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    // Lazy loading for collections
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderItem> items;
}
```

### 3.2 How It Works

1. When an entity is loaded, lazy relationships are not fetched immediately
2. A proxy object is created instead of the actual entity/collection
3. When a method is called on the proxy, it triggers loading from the database
4. The loaded entity is cached for subsequent accesses

### 3.3 LazyProxyFactory

The `LazyProxyFactory` creates proxies for lazy relationships:

```java
LazyProxyFactory proxyFactory = new LazyProxyFactory(
    metadataParser,
    entityLoader,  // Function to load entities by ID
    metadataCache
);

// Create ManyToOne proxy
User userProxy = proxyFactory.createManyToOneProxy(
    User.class,
    orderEntity,
    userIdField  // Field containing the foreign key
);

// Create collection proxy
List<OrderItem> itemsProxy = proxyFactory.createCollectionProxy(
    List.class,
    OrderItem.class,
    orderEntity,
    itemsField
);
```

### 3.4 EntityPersister Lazy Loading Support

The `EntityPersister` has been updated to support lazy loading:

```java
// Load with lazy loading enabled
EntityPersister persister = new EntityPersister(
    connection,
    sqlGenerator,
    metadataLookup,
    lazyLoader,  // Function to load entities
    foreignKeyLookup
);

// Load entity (relationships will be lazy if marked LAZY)
Object entity = persister.load(metadata, id, relationResolver);
```

### 3.5 Fetch Type in FieldMetadata

The `FieldMetadata` class now includes fetch type information:

```java
// Check fetch type
FieldMetadata field = metadata.getRelationship("user");
if (field.getFetchType() == FetchType.LAZY) {
    // This relationship is lazy loaded
}

// Convenience methods
field.isLazy();      // true if FetchType.LAZY
field.isEager();     // true if FetchType.EAGER
```

---

## 4. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    Mini-Hibernate Runtime                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐    ┌───────────────┐    ┌─────────────────┐  │
│  │EnhancedJPQL  │    │NativeQuery    │    │LazyProxyFactory │  │
│  │Parser        │    │Mapper         │    │                 │  │
│  └──────┬───────┘    └───────┬───────┘    └────────┬────────┘  │
│         │                    │                      │           │
│         │                    │                      │           │
│         ▼                    ▼                      ▼           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              MiniTypedQueryImpl                          │   │
│  │  - JPQL execution                                        │   │
│  │  - Native query execution                                │   │
│  │  - Entity/DTO mapping                                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              EntityPersister                             │   │
│  │  - CRUD operations                                      │   │
│  │  - Lazy loading support                                 │   │
│  │  - Type conversion                                      │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              MetadataParser                              │   │
│  │  - Parses @Entity, @ManyToOne, @OneToMany, etc.         │   │
│  │  - Extracts fetch type from annotations                  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. Migration Guide

### From SimpleJPQLParser to EnhancedJPQLParser

The new parser is fully backward compatible. Existing queries will continue to work.

**Before:**
```java
SimpleJPQLParser.parse(jpql, metadata);
```

**After:**
```java
EnhancedJPQLParser.parse(jpql, metadata);
```

### Adding Lazy Loading to Existing Entities

1. **Add `fetch = FetchType.LAZY`** to your relationship annotations:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id")
private User user;
```

2. **No code changes required** - the persistence context will automatically create proxies for lazy relationships.

---

## 6. Performance Considerations

### Lazy Loading

- **N+1 Query Problem**: Be careful when iterating over lazy-loaded collections. Each access may trigger a separate query.
- **Use JOIN FETCH in JPQL**: When you need to load lazy relationships eagerly, use JOIN FETCH:

```java
SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id
```

### Native Queries

- Native queries bypass some ORM optimizations. Use them for complex queries where JPQL is insufficient.
- Consider using `ResultSet` mapping instead of constructor expressions for better performance when dealing with large result sets.

### Aggregations

- Aggregation queries return scalar values, not entities. Use `Number.class` or `Object.class` as the result type:

```java
TypedQuery<Long> query = em.createQuery(
    "SELECT COUNT(u) FROM User u",
    Long.class
);
Long count = query.getSingleResult();
```

---

## 7. Limitations

### JPQL Parser

- Limited subquery support
- No CASE expressions
- No FULL JOIN support

### Native Query Mapping

- No `@SqlResultSetMapping` annotation support (yet)
- Constructor expressions must match exact parameter order

### Lazy Loading

- No bytecode enhancement (uses Java Dynamic Proxy)
- Collection proxies are basic implementations
- No batch loading optimization
