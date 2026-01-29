# 🗺️ Implementation Roadmap

## Project Status

✅ **Phase 1: Core Foundation** - DONE
- Annotations: `@Entity`, `@Id`, `@Column`, `@GeneratedValue`
- Metadata: `EntityMetadata`, `FieldMetadata`, `MetadataParser`

✅ **Phase 2-5: Core ORM** - SKELETON DONE (needs implementation)
- Session: `MiniSessionFactory`, `MiniSession`, `MiniTransaction`, `EntityState`
- Persistence: `PersistenceContext`, `EntityKey`, `EntityPersister`
- SQL: `SQLGenerator`
- Query: `MiniQuery`

---

## Next Steps

### 🔴 Priority 1: Fix & Test Basic CRUD

**Goal**: `persist()` và `find()` hoạt động với H2

```java
// Target working code
try (MiniSession session = sf.openSession()) {
    MiniTransaction tx = session.beginTransaction();
    
    User user = new User();
    user.setName("John");
    session.persist(user);
    
    tx.commit();  // INSERT executed here
    
    User found = session.find(User.class, user.getId());
    assert found.getName().equals("John");
}
```

**Tasks**:
- [ ] Run `MiniHibernateDemo` and fix any runtime errors
- [ ] Write unit test for `MetadataParser`
- [ ] Write integration test for `persist()` + `find()`

---

### 🟡 Priority 2: Dirty Checking

**Goal**: Thay đổi entity → auto UPDATE on flush

```java
User user = session.find(User.class, 1L);
user.setName("Jane");  // Changed!
tx.commit();  // Should generate UPDATE
```

**Tasks**:
- [ ] Verify `PersistenceContext.detectDirtyEntities()` works
- [ ] Test snapshot comparison
- [ ] Integration test for dirty checking

---

### 🟡 Priority 3: Query API

**Goal**: Simple queries hoạt động

```java
List<User> users = session.createQuery(User.class)
    .where("email", "test@example.com")
    .getResultList();
```

**Tasks**:
- [ ] Test `MiniQuery` with H2
- [ ] Add more query conditions (like, in, between)
- [ ] Pagination test

---

### 🟢 Priority 4: Advanced Features (Optional)

| Feature | Complexity | Files to modify |
|---------|------------|-----------------|
| Relationships (@ManyToOne) | High | New classes needed |
| Lazy Loading | High | Proxy/ByteBuddy |
| Named Queries | Medium | `@Query` annotation |
| Schema Generation | Medium | DDL generator |
| Second-Level Cache | Medium | Cache provider |

---

## File Structure

```
src/main/java/com/minihibernate/
├── annotation/
│   ├── Entity.java           ✅
│   ├── Id.java               ✅
│   ├── Column.java           ✅
│   ├── GeneratedValue.java   ✅
│   └── GenerationType.java   ✅
├── metadata/
│   ├── EntityMetadata.java   ✅
│   ├── FieldMetadata.java    ✅
│   └── MetadataParser.java   ✅
├── session/
│   ├── MiniSessionFactory.java   ✅
│   ├── MiniSession.java          ✅
│   ├── MiniTransaction.java      ✅
│   └── EntityState.java          ✅
├── persist/
│   ├── PersistenceContext.java   ✅
│   ├── EntityKey.java            ✅
│   └── EntityPersister.java      ✅
├── sql/
│   └── SQLGenerator.java         ✅
├── query/
│   └── MiniQuery.java            ✅
├── example/
│   └── User.java                 ✅
└── MiniHibernateDemo.java        ✅
```

---

## How to Run

```bash
# Compile
mvn compile

# Run demo
mvn exec:java -Dexec.mainClass="com.minihibernate.MiniHibernateDemo"

# Run tests
mvn test
```

---

## Learning Focus

When implementing each phase, focus on:

| Phase | Key Concepts to Learn |
|-------|----------------------|
| Metadata | Reflection API, Annotations, Caching |
| Session | Factory Pattern, Resource Management |
| PersistenceContext | Identity Map, Snapshot Pattern |
| Dirty Checking | Value Comparison, Change Detection |
| SQL Generation | String Building, PreparedStatement |
| Transaction | JDBC Transaction, Rollback handling |
