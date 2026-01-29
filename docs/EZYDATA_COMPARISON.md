# 🔍 So sánh EzyData vs Hibernate

## TL;DR

| Aspect | EzyData | Hibernate | Mini-Hibernate cần implement |
|--------|---------|-----------|------------------------------|
| **Bản chất** | Wrapper/Abstraction | Full ORM | Full ORM |
| **Persistence Context** | ❌ Delegate cho JPA | ✅ Tự implement | ✅ Cần |
| **Entity State** | ❌ Không track | ✅ 4 states | ✅ Cần |
| **SQL Generation** | ❌ Dùng JPQL | ✅ Tự generate | ✅ Cần |
| **Dirty Checking** | ❌ Không có | ✅ Snapshot compare | ✅ Cần |
| **Query Method** | ✅ Parse từ method name | ❌ Không có | 🔄 Optional |

---

## EzyData là gì?

EzyData là **abstraction layer** trên JPA/Hibernate, **KHÔNG** phải ORM đầy đủ.

```
┌─────────────────────────────┐
│       Your Application      │
├─────────────────────────────┤
│        EzyData API          │  ← Repository pattern
│   EzyJpaRepository, etc.    │
├─────────────────────────────┤
│     JPA / EntityManager     │  ← EzyData delegates here
├─────────────────────────────┤
│      Hibernate ORM          │  ← Real ORM work happens here
├─────────────────────────────┤
│          JDBC               │
└─────────────────────────────┘
```

---

## Những gì có thể học từ EzyData

### 1. Repository Interface Design

```java
// File: EzyDatabaseRepository.java
public interface EzyDatabaseRepository<I, E> {
    String PREFIX_FIND_BY = "findBy";
    String PREFIX_COUNT_BY = "countBy";
    String PREFIX_DELETE_BY = "deleteBy";
}
```

**Áp dụng**: Định nghĩa constants cho method prefix parsing.

### 2. Query Method Parsing

```java
// File: EzyQueryMethod.java
// findByEmailAndStatus → conditions: [Email, Status]
private static EzyQueryConditionChain getConditionChain(EzyMethod method) {
    String methodName = method.getName();
    if (methodName.startsWith(PREFIX_FIND_BY)) {
        String chain = methodName.substring(PREFIX_FIND_BY.length());
        // Split "EmailAndStatus" by "And" → [Email, Status]
    }
}
```

**Áp dụng**: Parse tên method thành query conditions (giống Spring Data).

### 3. Factory/Builder Pattern

```java
// File: EzyDatabaseContextBuilder.java
public class EzyDatabaseContextBuilder {
    protected Set<String> packagesToScan;
    protected Set<Class<?>> repositoryClasses;
    
    public EzyDatabaseContextBuilder scan(String packageName) {...}
    public EzyDatabaseContextBuilder repositoryClass(Class<?> repoClass) {...}
    public EzyDatabaseContext build() {...}
}
```

**Áp dụng**: Builder pattern cho `MiniConfiguration`/`MiniSessionFactory`.

### 4. Generic Repository với Reflection

```java
// File: EzyJpaRepository.java
protected Class<E> getEntityType() {
    Type genericSuperclass = getClass().getGenericSuperclass();
    ParameterizedType pt = (ParameterizedType) genericSuperclass;
    return (Class<E>) pt.getActualTypeArguments()[1];
}
```

**Áp dụng**: Lấy entity type từ generic parameter.

---

## Những gì KHÔNG học được từ EzyData

### ❌ Persistence Context (First-Level Cache)

EzyData không implement, delegate cho JPA:
```java
// EzyData: Mỗi operation tạo EntityManager mới
public E findById(I id) {
    EntityManager em = databaseContext.createEntityManager();
    try {
        // Query here
    } finally {
        em.close();  // No caching between calls!
    }
}
```

Hibernate thực sự:
```java
// Hibernate: Identity Map trong Session
User user1 = session.find(User.class, 1L);
User user2 = session.find(User.class, 1L);
assert user1 == user2;  // Same instance từ cache!
```

### ❌ Entity State Management

EzyData không track, delegate cho `EntityManager.merge()`:
```java
public void save(E entity) {
    em.merge(entity);  // JPA handles state
}
```

Hibernate thực sự track 4 states:
- **Transient**: `new User()` - chưa được quản lý
- **Managed**: `persist()` - được track, auto-flush
- **Detached**: `session.close()` - có ID nhưng không track
- **Removed**: `remove()` - sẽ DELETE khi flush

### ❌ Dirty Checking

EzyData không implement:
```java
// Không có snapshot, không detect changes
```

Hibernate thực sự:
```java
User user = session.find(User.class, 1L);
// Hibernate snapshot: {name: "John", email: "john@ex.com"}

user.setName("Jane");  // Change in memory

session.flush();
// Compare current values với snapshot
// name changed! → Generate UPDATE SQL
```

### ❌ SQL Generation

EzyData dùng JPQL (JPA query language):
```java
String queryString = "select e from " + entityType.getName() + " e";
entityManager.createQuery(queryString);
```

Hibernate tự generate native SQL từ metadata.

---

## Roadmap: Học từ đâu?

```
Phase 1-2: Annotations, Metadata
    → Tham khảo: EzyData's reflection utils
    
Phase 3: Persistence Context  
    → KHÔNG có trong EzyData, tự implement!
    
Phase 4: CRUD Operations
    → Tham khảo: EzyJpaRepository (nhưng dùng JDBC thay EntityManager)
    
Phase 5: Transaction
    → Tham khảo: EzyJpaRepository.save() transaction handling
    
Phase 6: Query API
    → Tham khảo: EzyQueryMethod parsing
    
Phase 7-8: Relationships, Lazy Loading
    → KHÔNG có trong EzyData, tự implement!
```

---

## File tham khảo

| Concept | EzyData File |
|---------|--------------|
| Repository interface | `ezydata-database/.../EzyDatabaseRepository.java` |
| JPA Repository impl | `ezydata-jpa/.../repository/EzyJpaRepository.java` |
| Context builder | `ezydata-database/.../EzyDatabaseContextBuilder.java` |
| Query parsing | `ezydata-database/.../query/EzyQueryMethod.java` |
| Query conditions | `ezydata-database/.../query/EzyQueryCondition.java` |
