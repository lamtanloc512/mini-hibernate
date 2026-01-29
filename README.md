# Mini Hibernate

A lightweight ORM framework for learning how Hibernate/JPA works internally.

## 🎯 Goals

- Understand Hibernate/JPA architecture through hands-on implementation
- Learn advanced Java techniques: Reflection, Dynamic Proxy, Annotations, SPI
- Create a usable library that can be used like Spring Data JPA

## 📦 Package Structure

```
com.minihibernate/
├── annotation/    # Entity mapping annotations
├── api/           # Public interfaces (Facade pattern)
├── internal/      # Implementation classes (hidden from users)
├── metadata/      # Reflection-based entity parsing
├── persist/       # Persistence context & Identity Map
├── query/         # Query builder
├── repository/    # Spring Data-style @Query support
├── session/       # Session & Transaction management
├── spi/           # Database dialect abstraction (SPI pattern)
└── sql/           # SQL generation
```

---

## 🏗️ Architecture & Design Patterns

### 1. `annotation/` - Entity Mapping
**Purpose:** Define how Java classes map to database tables.

```java
@Entity(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_name")
    private String name;
}
```

**Why:** Hibernate uses annotations to avoid XML configuration. We parse these at runtime using Reflection.

---

### 2. `api/` - Public Interfaces (Facade Pattern)
**Purpose:** Define the contract users code against.

| Interface | Description |
|-----------|-------------|
| `MiniEntityManager` | Main entry point for CRUD operations |
| `MiniEntityManagerFactory` | Creates EntityManager instances (expensive, create once) |
| `MiniTransaction` | Transaction control |
| `MiniTypedQuery<T>` | Type-safe query interface |

**Why Interfaces?**
```
┌─────────────────────────────────────────────────────────────┐
│  User Code                                                  │
│  MiniEntityManager em = factory.createEntityManager();      │
│  em.persist(user);  ← User only sees interface methods      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  Implementation (hidden)                                    │
│  MiniEntityManagerImpl.persist() ← Actual logic here        │
│  - Manages PersistenceContext                               │
│  - Schedules INSERT                                         │
│  - Handles dirty checking                                   │
└─────────────────────────────────────────────────────────────┘
```

**Benefits:**
- Implementation can change without breaking user code
- Easier to add caching, logging, proxying
- Same pattern used by JPA/Hibernate

---

### 3. `internal/` - Hidden Implementations
**Purpose:** Contains the actual implementation code.

| Class | Implements |
|-------|-----------|
| `MiniEntityManagerImpl` | `MiniEntityManager` |
| `MiniEntityManagerFactoryImpl` | `MiniEntityManagerFactory` |
| `MiniTransactionImpl` | `MiniTransaction` |
| `MiniTypedQueryImpl` | `MiniTypedQuery` |

**Why Hide?**
- Users don't need to know implementation details
- Can swap implementations (e.g., different database support)
- Prevents coupling to internal classes

---

### 4. `metadata/` - Entity Metadata Parsing
**Purpose:** Parse annotations using Reflection to understand entity structure.

```java
MetadataParser parser = new MetadataParser();
EntityMetadata metadata = parser.parse(User.class);

// Now we know:
metadata.getTableName();      // "users"
metadata.getIdField();        // id field info
metadata.getColumns();        // all mapped columns
```

**Key Classes:**
- `MetadataParser` - Parses @Entity, @Id, @Column annotations
- `EntityMetadata` - Holds parsed table/entity info
- `FieldMetadata` - Holds per-field mapping info

---

### 5. `persist/` - Persistence Context (Identity Map + Unit of Work)

**Purpose:** Track entity state and manage first-level cache.

```
┌─────────────────────────────────────────────────────────────┐
│  PersistenceContext                                         │
│                                                             │
│  Identity Map (First-Level Cache):                          │
│  ┌──────────────┬───────────────────────────┐               │
│  │ EntityKey    │ Entity Instance           │               │
│  ├──────────────┼───────────────────────────┤               │
│  │ User#1       │ User{id=1, name="John"}   │               │
│  │ User#2       │ User{id=2, name="Jane"}   │               │
│  └──────────────┴───────────────────────────┘               │
│                                                             │
│  Action Queues (Unit of Work):                              │
│  - Insert Queue: [new User, new Order]                      │
│  - Delete Queue: [old Product]                              │
│  - Snapshots: {User#1: original state for dirty checking}   │
└─────────────────────────────────────────────────────────────┘
```

**Key Features:**
- **Identity Map:** Same ID → Same instance (no duplicates)
- **Dirty Checking:** Compare current vs snapshot → auto UPDATE
- **Action Queues:** Batch INSERT/DELETE on flush()

---

### 6. `session/` - Session & Transaction

**Purpose:** Manage database connection lifecycle and transactions.

```java
try (MiniSession session = factory.openSession()) {
    MiniTransaction tx = session.beginTransaction();
    
    session.persist(user);
    session.find(User.class, 1L);  // May hit cache
    
    tx.commit();  // Flushes all changes to DB
}
```

**Pattern:** Session-per-Request
- One Session per HTTP request/thread
- SessionFactory is expensive (connection pool) → create once
- Session is cheap → create per request

---

### 7. `repository/` - Spring Data @Query Pattern
**Purpose:** Define queries via annotations on interface methods.

```java
public interface UserRepository extends MiniRepository<User, Long> {
    
    @Query("SELECT * FROM users WHERE email = ?1")
    Optional<User> findByEmail(String email);
    
    @Query("SELECT * FROM users WHERE age BETWEEN ?1 AND ?2")
    List<User> findByAgeBetween(int min, int max);
}

// Usage:
UserRepository repo = factory.createRepository(UserRepository.class);
repo.findByEmail("test@example.com");  // Executes SQL automatically!
```

**How it works:** Java Dynamic Proxy
```
┌─────────────────────────────────────────────────────────────┐
│  UserRepository interface (no implementation!)              │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ findByEmail(String) → @Query("SELECT...")              ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
                              │
                    Proxy.newProxyInstance()
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  InvocationHandler                                          │
│  1. Intercept method call                                   │
│  2. Read @Query annotation                                  │
│  3. Execute SQL with parameters                             │
│  4. Map ResultSet to entities                               │
│  5. Return result                                           │
└─────────────────────────────────────────────────────────────┘
```

---

### 8. `spi/` - Database Dialect (SPI Pattern)
**Purpose:** Abstract database-specific SQL differences.

```java
public interface Dialect {
    String getSqlType(Class<?> javaType);  // Long → BIGINT
    String getIdentityColumnString();       // AUTO_INCREMENT vs SERIAL
    String getLimitString(String sql, int offset, int limit);
}
```

**Implementations:**
- `MySQLDialect` - MySQL specific
- `H2Dialect` - H2 specific

**SPI Discovery:** Automatically loaded via `META-INF/services/`

---

### 9. `sql/` - SQL Generation
**Purpose:** Generate SQL statements from metadata.

```java
SQLGenerator gen = new SQLGenerator();
gen.generateInsert(metadata);   // INSERT INTO users (name, email) VALUES (?, ?)
gen.generateSelect(metadata);   // SELECT id, name, email FROM users WHERE id = ?
gen.generateUpdate(metadata);   // UPDATE users SET name=?, email=? WHERE id=?
```

---

## 🚀 Quick Start

```java
// 1. Create factory (once per application)
MiniEntityManagerFactory factory = MiniEntityManagerFactoryImpl.builder()
    .url("jdbc:mysql://localhost:3306/mydb")
    .username("root")
    .password("password")
    .addEntityClass(User.class)
    .build();

// 2. Use EntityManager (per request)
try (MiniEntityManager em = factory.createEntityManager()) {
    em.getTransaction().begin();
    
    User user = new User();
    user.setName("John");
    em.persist(user);
    
    em.getTransaction().commit();
}

// 3. Or use Repository pattern
RepositoryFactory repoFactory = new RepositoryFactory(factory);
UserRepository userRepo = repoFactory.createRepository(UserRepository.class);
userRepo.findByEmail("john@example.com");
```

## 📚 Documentation

| File | Description |
|------|-------------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Detailed architecture diagrams |
| [docs/LEARNING_GUIDE.md](docs/LEARNING_GUIDE.md) | Step-by-step learning guide |
| [docs/HIBERNATE_SPRINGBOOT_GUIDE.md](docs/HIBERNATE_SPRINGBOOT_GUIDE.md) | Hibernate in Spring Boot |

## ✅ Implemented Features

| Feature | Status |
|---------|--------|
| Entity annotations (@Entity, @Id, @Column) | ✅ |
| Metadata parsing (Reflection) | ✅ |
| Session/EntityManager | ✅ |
| Transaction management | ✅ |
| First-level cache (Identity Map) | ✅ |
| Dirty checking | ✅ |
| Query builder | ✅ |
| @Query annotation (Dynamic Proxy) | ✅ |
| Database dialect (SPI) | ✅ |
| Repository pattern | ✅ |

## 🔗 References

- [Hibernate ORM Source](https://github.com/hibernate/hibernate-orm)
- [JPA Specification](https://jakarta.ee/specifications/persistence/)
