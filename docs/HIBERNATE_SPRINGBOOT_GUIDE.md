# 🔥 Hibernate Deep Dive - Guide cho Spring Boot

## Mục tiêu
Hiểu cách Hibernate hoạt động để sử dụng hiệu quả trong Spring Boot.

---

## 1. Kiến trúc Hibernate trong Spring Boot

```
┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                  │
├─────────────────────────────────────────────────────────────┤
│  @Repository                                                │
│  UserRepository extends JpaRepository<User, Long>           │
├─────────────────────────────────────────────────────────────┤
│                Spring Data JPA                              │
│    (Auto-generate implementation từ interface)              │
├─────────────────────────────────────────────────────────────┤
│                JPA API (EntityManager)                      │
├─────────────────────────────────────────────────────────────┤
│                Hibernate ORM                                │
│  ┌──────────────┬──────────────┬──────────────┐             │
│  │ Session      │ Persistence  │ SQL          │             │
│  │ Factory      │ Context      │ Generator    │             │
│  └──────────────┴──────────────┴──────────────┘             │
├─────────────────────────────────────────────────────────────┤
│                   HikariCP (Connection Pool)                │
├─────────────────────────────────────────────────────────────┤
│                   JDBC Driver                               │
├─────────────────────────────────────────────────────────────┤
│                   Database (MySQL, PostgreSQL)              │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Entity Lifecycle (Quan trọng nhất!)

### 4 trạng thái của Entity

```java
// 1. TRANSIENT - Mới tạo, chưa được quản lý
User user = new User("John");  // transient

// 2. MANAGED - Được Hibernate track, auto-sync với DB
userRepository.save(user);     // user giờ là managed
user.setName("Jane");          // Hibernate BIẾT thay đổi này!

// 3. DETACHED - Đã có ID nhưng session đóng
// Khi request kết thúc, entity trở thành detached

// 4. REMOVED - Đánh dấu xóa
userRepository.delete(user);   // Sẽ DELETE khi flush
```

### Diagram trạng thái

```
        new()              save()/persist()
    ┌─────────┐           ┌─────────────────┐
    │TRANSIENT│──────────►│    MANAGED      │
    └─────────┘           │  (First-Level   │
         ▲                │    Cache)       │
         │                └────────┬────────┘
         │                         │
    merge()                   detach()/
         │                   close()/clear()
         │                         │
    ┌────┴────┐           ┌────────▼────────┐
    │ REMOVED │◄──────────│   DETACHED      │
    └─────────┘  delete() └─────────────────┘
```

---

## 3. First-Level Cache (Session Cache)

### Cách hoạt động

```java
@Transactional
public void example() {
    // Query 1: SELECT * FROM users WHERE id = 1
    User user1 = userRepository.findById(1L).get();
    
    // Query 2: KHÔNG có SQL! Lấy từ cache
    User user2 = userRepository.findById(1L).get();
    
    // Cùng một instance
    assert user1 == user2;  // true!
}
```

### Identity Map Pattern

```
First-Level Cache (trong Session)
┌──────────────────────────────────────┐
│  Key: EntityKey(User.class, 1L)      │
│  Value: User{id=1, name="John"}      │
├──────────────────────────────────────┤
│  Key: EntityKey(User.class, 2L)      │
│  Value: User{id=2, name="Jane"}      │
└──────────────────────────────────────┘
```

---

## 4. Dirty Checking (Auto UPDATE)

### Cách Hibernate biết entity thay đổi

```java
@Transactional
public void updateUser(Long id) {
    User user = userRepository.findById(id).get();
    // Hibernate snapshot: {name: "John", email: "john@ex.com"}
    
    user.setName("Jane");  // Thay đổi trong memory
    
    // KHÔNG cần gọi save()!
    // Khi transaction commit, Hibernate:
    // 1. So sánh current values với snapshot
    // 2. Phát hiện name thay đổi
    // 3. Generate: UPDATE users SET name='Jane' WHERE id=?
}
```

### Snapshot Comparison

```
Original Snapshot          Current Values
┌─────────────────┐       ┌─────────────────┐
│ name: "John"    │  vs   │ name: "Jane"    │  ← CHANGED!
│ email: "j@ex"   │       │ email: "j@ex"   │  ← same
└─────────────────┘       └─────────────────┘
                ↓
        UPDATE users SET name='Jane' WHERE id=1
```

---

## 5. Flush Modes

### Khi nào Hibernate sync với DB?

```java
// FlushModeType.AUTO (default)
// Flush tự động trước mỗi query để đảm bảo data consistent

@Transactional
public void example() {
    User user = new User("John");
    userRepository.save(user);       // Chưa INSERT!
    
    // Hibernate PHẢI flush trước query này
    // để đảm bảo count() có user mới
    long count = userRepository.count(); // Flush → INSERT → SELECT COUNT
}
```

### Manual Flush

```java
@Autowired
EntityManager em;

@Transactional
public void batchInsert(List<User> users) {
    for (int i = 0; i < users.size(); i++) {
        em.persist(users.get(i));
        
        if (i % 50 == 0) {
            em.flush();  // Execute pending INSERTs
            em.clear();  // Clear first-level cache (memory)
        }
    }
}
```

---

## 6. Lazy Loading & N+1 Problem

### @ManyToOne (default EAGER)

```java
@Entity
public class Order {
    @ManyToOne  // EAGER by default
    private User user;  // Load ngay khi load Order
}
```

### @OneToMany (default LAZY)

```java
@Entity
public class User {
    @OneToMany(mappedBy = "user")  // LAZY by default
    private List<Order> orders;    // Chỉ load khi access
}
```

### N+1 Problem

```java
// BAD: N+1 queries
List<User> users = userRepository.findAll();  // 1 query
for (User user : users) {
    user.getOrders().size();  // N queries! (1 per user)
}

// GOOD: JOIN FETCH
@Query("SELECT u FROM User u JOIN FETCH u.orders")
List<User> findAllWithOrders();  // 1 query with JOIN
```

---

## 7. Transaction trong Spring Boot

### @Transactional annotation

```java
@Service
public class UserService {
    
    @Transactional  // Bắt đầu transaction
    public void createUser(UserDto dto) {
        User user = new User(dto.getName());
        userRepository.save(user);
        
        // Nếu exception → ROLLBACK
        // Nếu success → COMMIT + flush
    }
    
    @Transactional(readOnly = true)  // Optimization
    public User getUser(Long id) {
        return userRepository.findById(id).orElse(null);
    }
}
```

### Transaction Propagation

```java
@Transactional
public void methodA() {
    // Transaction A bắt đầu
    methodB();  // Dùng chung transaction A
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void methodB() {
    // Transaction B MỚI, độc lập với A
}
```

---

## 8. Spring Data JPA Repository

### Query Methods (Magic!)

```java
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Spring tự generate: SELECT * FROM users WHERE email = ?
    User findByEmail(String email);
    
    // SELECT * FROM users WHERE name LIKE ? AND status = ?
    List<User> findByNameContainingAndStatus(String name, String status);
    
    // SELECT * FROM users WHERE created_at > ?
    List<User> findByCreatedAtAfter(LocalDateTime date);
    
    // Custom JPQL
    @Query("SELECT u FROM User u WHERE u.department.id = :deptId")
    List<User> findByDepartment(@Param("deptId") Long deptId);
    
    // Native SQL
    @Query(value = "SELECT * FROM users WHERE status = 1", nativeQuery = true)
    List<User> findActiveUsers();
}
```

---

## 9. Common Pitfalls & Best Practices

### ❌ LazyInitializationException

```java
// WRONG: Access lazy collection outside transaction
User user = userRepository.findById(1L).get();  // Transaction ends
user.getOrders().size();  // EXCEPTION! Session đã đóng

// FIX 1: JOIN FETCH
@Query("SELECT u FROM User u JOIN FETCH u.orders WHERE u.id = :id")
User findByIdWithOrders(@Param("id") Long id);

// FIX 2: @Transactional on service method
@Transactional
public UserDto getUser(Long id) {
    User user = userRepository.findById(id).get();
    user.getOrders().size();  // OK, within transaction
    return new UserDto(user);
}

// FIX 3: Entity Graph
@EntityGraph(attributePaths = {"orders"})
Optional<User> findById(Long id);
```

### ❌ Modifying without @Transactional

```java
// WRONG: No transaction, dirty checking won't work
public void updateName(Long id, String name) {
    User user = userRepository.findById(id).get();
    user.setName(name);  // Thay đổi KHÔNG được save!
}

// RIGHT
@Transactional
public void updateName(Long id, String name) {
    User user = userRepository.findById(id).get();
    user.setName(name);  // Auto-save on commit!
}
```

### ✅ Best Practices

```java
// 1. Luôn dùng @Transactional cho write operations
@Transactional
public void createUser(...) { }

// 2. Dùng readOnly cho reads
@Transactional(readOnly = true)
public User getUser(Long id) { }

// 3. Batch processing với flush/clear
@Transactional
public void batchInsert(List<User> users) {
    for (int i = 0; i < users.size(); i++) {
        em.persist(users.get(i));
        if (i % 100 == 0) {
            em.flush();
            em.clear();
        }
    }
}

// 4. Dùng DTO projection cho reports
@Query("SELECT new com.example.UserDto(u.id, u.name) FROM User u")
List<UserDto> findAllAsDto();
```

---

## 10. Configuration trong Spring Boot

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb
    username: root
    password: secret
    hikari:
      maximum-pool-size: 10
      
  jpa:
    hibernate:
      ddl-auto: validate  # Không auto-create tables!
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        generate_statistics: true  # Performance monitoring
        jdbc:
          batch_size: 20  # Batch inserts
        order_inserts: true
        order_updates: true
```

---

## Quick Reference

| Concept | Giải thích |
|---------|------------|
| **Session** | Unit of work, first-level cache |
| **Persistence Context** | Entities được track trong session |
| **Dirty Checking** | Auto-detect thay đổi, generate UPDATE |
| **Flush** | Sync pending changes với DB |
| **JPQL** | Object-oriented query language |
| **Lazy Loading** | Load data khi access, không load trước |
| **N+1 Problem** | Nhiều queries do lazy loading |
| **@Transactional** | Demarcate transaction boundaries |
