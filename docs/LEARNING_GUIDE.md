# 📚 Hướng Dẫn Học Hibernate/JPA Implementation

## 🎯 Bắt đầu từ đâu?

### Cách tiếp cận "Bottom-Up"

Thay vì học Hibernate từ cách sử dụng, chúng ta sẽ **tự xây dựng** một mini ORM để hiểu sâu cách nó hoạt động.

```
Level 1: JDBC cơ bản
    ↓
Level 2: Annotations + Reflection 
    ↓
Level 3: Metadata Parsing
    ↓
Level 4: Session + PersistenceContext
    ↓
Level 5: SQL Generation
    ↓
Level 6: Transaction Management
    ↓
Level 7: Caching + Lazy Loading
```

---

## 📖 Phase 1: Core Annotations & Metadata (Tuần 1-2)

### Mục tiêu
- Hiểu cách annotations hoạt động
- Parse entity class thành metadata

### Tasks

#### 1.1 Tạo Custom Annotations
```java
// Tạo các annotation giống JPA
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Entity {
    String table() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Id {
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {
    String name() default "";
    boolean nullable() default true;
    int length() default 255;
}
```

#### 1.2 Tạo Metadata Classes
```java
public class EntityMetadata {
    private Class<?> entityClass;
    private String tableName;
    private FieldMetadata idField;
    private List<FieldMetadata> columns;
    // getters, setters
}

public class FieldMetadata {
    private Field field;
    private String columnName;
    private boolean isId;
    private Class<?> javaType;
    // getters, setters
}
```

#### 1.3 Tạo MetadataParser
```java
public class MetadataParser {
    public EntityMetadata parse(Class<?> entityClass) {
        // Use reflection to read annotations
        // Build EntityMetadata
    }
}
```

### 📝 Bài tập
1. Viết unit test cho MetadataParser
2. Handle edge cases: field không có @Column, default table name

### 📚 Đọc thêm
- [Java Reflection Tutorial](https://www.baeldung.com/java-reflection)
- [Custom Annotations](https://www.baeldung.com/java-custom-annotation)

---

## 📖 Phase 2: Session Factory & Session (Tuần 3-4)

### Mục tiêu
- Hiểu pattern SessionFactory -> Session
- Implement connection management

### Tasks

#### 2.1 MiniConfiguration
```java
public class MiniConfiguration {
    private Properties properties;
    
    public MiniConfiguration() {
        this.properties = new Properties();
    }
    
    public MiniConfiguration setProperty(String key, String value) {
        properties.setProperty(key, value);
        return this;
    }
    
    public MiniConfiguration addAnnotatedClass(Class<?> entityClass) {
        // Register entity for scanning
        return this;
    }
    
    public MiniSessionFactory buildSessionFactory() {
        // Parse all entities
        // Setup connection pool
        // Return factory
    }
}
```

#### 2.2 MiniSessionFactory
```java
public class MiniSessionFactory implements Closeable {
    private DataSource dataSource;
    private Map<Class<?>, EntityMetadata> entityMetadataMap;
    
    public MiniSession openSession() {
        Connection conn = dataSource.getConnection();
        return new MiniSession(conn, entityMetadataMap);
    }
    
    @Override
    public void close() {
        // Close connection pool
    }
}
```

#### 2.3 MiniSession
```java
public class MiniSession implements Closeable {
    private Connection connection;
    private MiniPersistenceContext persistenceContext;
    private MiniTransaction transaction;
    
    public void persist(Object entity) { }
    public <T> T find(Class<T> type, Object id) { }
    public void remove(Object entity) { }
    
    public MiniTransaction beginTransaction() { }
    public void flush() { }
    
    @Override
    public void close() { }
}
```

### 📝 Bài tập
1. Implement connection pool đơn giản (hoặc dùng HikariCP)
2. Viết test lifecycle: open -> operations -> close

---

## 📖 Phase 3: Persistence Context (Tuần 5-6)

### Mục tiêu
- Hiểu First-Level Cache
- Implement Identity Map pattern
- Entity state management

### Tasks

#### 3.1 EntityKey
```java
public class EntityKey {
    private Class<?> entityClass;
    private Object id;
    
    // equals() và hashCode() quan trọng!
}
```

#### 3.2 MiniPersistenceContext
```java
public class MiniPersistenceContext {
    // Identity Map - mỗi entity chỉ có 1 instance
    private Map<EntityKey, Object> entityMap = new HashMap<>();
    
    // Snapshot để dirty checking
    private Map<EntityKey, Object[]> snapshots = new HashMap<>();
    
    // Track entity states
    private Map<EntityKey, EntityState> states = new HashMap<>();
    
    public void addEntity(EntityKey key, Object entity) {
        entityMap.put(key, entity);
        snapshots.put(key, takeSnapshot(entity));
        states.put(key, EntityState.MANAGED);
    }
    
    public Object getEntity(EntityKey key) {
        return entityMap.get(key);
    }
    
    public boolean isDirty(EntityKey key) {
        Object entity = entityMap.get(key);
        Object[] original = snapshots.get(key);
        Object[] current = takeSnapshot(entity);
        return !Arrays.equals(original, current);
    }
    
    private Object[] takeSnapshot(Object entity) {
        // Use reflection to get all field values
    }
}
```

### 📝 Bài tập
1. Test Identity Map: load cùng entity 2 lần → same instance
2. Test dirty checking: modify entity → detect changes

---

## 📖 Phase 4: CRUD Operations (Tuần 7-8)

### Mục tiêu
- Generate SQL từ metadata
- Map ResultSet về Object

### Tasks

#### 4.1 SQLGenerator
```java
public class SQLGenerator {
    public String generateInsert(EntityMetadata meta) {
        // INSERT INTO table (col1, col2) VALUES (?, ?)
    }
    
    public String generateSelect(EntityMetadata meta) {
        // SELECT col1, col2 FROM table WHERE id = ?
    }
    
    public String generateUpdate(EntityMetadata meta) {
        // UPDATE table SET col1=?, col2=? WHERE id=?
    }
    
    public String generateDelete(EntityMetadata meta) {
        // DELETE FROM table WHERE id = ?
    }
}
```

#### 4.2 EntityPersister
```java
public class EntityPersister {
    private EntityMetadata metadata;
    private SQLGenerator sqlGenerator;
    
    public void insert(Connection conn, Object entity) {
        String sql = sqlGenerator.generateInsert(metadata);
        try (PreparedStatement ps = conn.prepareStatement(sql, 
                Statement.RETURN_GENERATED_KEYS)) {
            setParameters(ps, entity);
            ps.executeUpdate();
            
            // Get generated ID
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                setId(entity, rs.getObject(1));
            }
        }
    }
    
    public Object load(Connection conn, Object id) {
        String sql = sqlGenerator.generateSelect(metadata);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapResultSet(rs);
            }
        }
        return null;
    }
    
    private Object mapResultSet(ResultSet rs) {
        // Create instance using reflection
        // Set field values from ResultSet
    }
}
```

### 📝 Bài tập
1. Handle null values
2. Support các Java types: String, Long, Integer, Date, LocalDateTime

---

## 📖 Phase 5: Transaction Management (Tuần 9-10)

### Mục tiêu
- Hiểu transaction boundaries
- Implement flush on commit

### Tasks

#### 5.1 MiniTransaction
```java
public class MiniTransaction {
    private Connection connection;
    private MiniSession session;
    private boolean active = false;
    
    public void begin() {
        connection.setAutoCommit(false);
        active = true;
    }
    
    public void commit() {
        session.flush(); // Write all changes
        connection.commit();
        active = false;
    }
    
    public void rollback() {
        connection.rollback();
        active = false;
    }
}
```

#### 5.2 Action Queue trong Session
```java
public class MiniSession {
    private List<EntityAction> insertions = new ArrayList<>();
    private List<EntityAction> updates = new ArrayList<>();
    private List<EntityAction> deletions = new ArrayList<>();
    
    public void flush() {
        // Thực hiện theo thứ tự: INSERT → UPDATE → DELETE
        for (EntityAction action : insertions) {
            action.execute();
        }
        // dirty check để tìm updates
        detectDirtyEntities();
        for (EntityAction action : updates) {
            action.execute();
        }
        for (EntityAction action : deletions) {
            action.execute();
        }
        clearActionQueues();
    }
}
```

### 📝 Bài tập
1. Test rollback: modify → rollback → verify no change in DB
2. Test ordering: insert parent → insert child (foreign key)

---

## 📖 Phase 6: Simple Query API (Tuần 11-12)

### Mục tiêu
- Implement simple query by field
- Named queries

### Tasks

```java
public class MiniQuery<T> {
    private Class<T> entityClass;
    private Map<String, Object> parameters = new HashMap<>();
    private String whereClause;
    
    public MiniQuery<T> where(String field, Object value) {
        // Build WHERE clause
        return this;
    }
    
    public List<T> getResultList() {
        // Generate SQL
        // Execute
        // Map results
    }
    
    public T getSingleResult() {
        List<T> results = getResultList();
        if (results.size() != 1) {
            throw new NonUniqueResultException();
        }
        return results.get(0);
    }
}

// Usage
List<User> users = session.createQuery(User.class)
    .where("email", "test@example.com")
    .getResultList();
```

---

## 📖 Phase 7: Relationships (Tuần 13-16)

### Mục tiêu
- @ManyToOne, @OneToMany
- Cascade operations
- Lazy loading với Proxy

### Tasks

#### 7.1 Relationship Annotations
```java
@ManyToOne
private Department department;

@OneToMany(mappedBy = "department")
private List<Employee> employees;
```

#### 7.2 Lazy Loading với Java Proxy
```java
public class LazyInitializer implements InvocationHandler {
    private Object target;
    private boolean initialized = false;
    private MiniSession session;
    private Class<?> entityClass;
    private Object id;
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (!initialized) {
            target = session.find(entityClass, id);
            initialized = true;
        }
        return method.invoke(target, args);
    }
}

// Tạo proxy
Object proxy = Proxy.newProxyInstance(
    classLoader,
    new Class<?>[] { entityClass },
    new LazyInitializer(session, entityClass, id)
);
```

---

## 🏆 Milestones & Checkpoints

| Milestone | Deliverable | Test |
|-----------|-------------|------|
| M1 | Parse @Entity class thành metadata | Unit test |
| M2 | SessionFactory + Session lifecycle | Integration test |
| M3 | persist() và find() hoạt động | CRUD test với H2 |
| M4 | Transaction commit/rollback | Transaction test |
| M5 | PersistenceContext caching | Identity map test |
| M6 | Dirty checking + auto UPDATE | Update detection test |
| M7 | Simple WHERE queries | Query test |
| M8 | @ManyToOne relationship | Relationship test |
| M9 | Lazy loading | Lazy load test |

---

## 📚 Study Resources

### Source Code References
1. **Hibernate ORM** - Full implementation
   - `org.hibernate.internal.SessionImpl`
   - `org.hibernate.persister.entity.AbstractEntityPersister`
   - `org.hibernate.engine.spi.PersistenceContext`

2. **MyBatis** - Simpler ORM, easier to understand
   - SQL mapping focused
   - Good for understanding ResultSet mapping

### Books
1. "Java Persistence with Hibernate" - Christian Bauer
2. "Pro JPA 2" - Mike Keith

### Online
- [Hibernate User Guide](https://docs.jboss.org/hibernate/orm/current/userguide/)
- [Vlad Mihalcea's Blog](https://vladmihalcea.com/) - Deep dive articles
- [Baeldung JPA/Hibernate](https://www.baeldung.com/learn-jpa-hibernate)
