# Mini Hibernate - Learning JPA/ORM Implementation

Dự án học tập để hiểu sâu về cách Hibernate/JPA hoạt động bằng cách tự xây dựng một mini ORM framework.

## 🎯 Mục tiêu

1. **Hiểu kiến trúc Hibernate/JPA** - Từ high-level đến implementation details
2. **Thực hành xây dựng ORM** - Viết code thực tế thay vì chỉ đọc lý thuyết
3. **Master các kỹ thuật Java nâng cao** - Reflection, Proxy, Annotations, JDBC

## 📚 Tài liệu

| File | Mô tả |
|------|-------|
| [docs/LEARNING_GUIDE.md](docs/LEARNING_GUIDE.md) | Hướng dẫn học tập từng bước |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Kiến trúc và diagrams |
| [docs/KNOWLEDGE_REQUIREMENTS.md](docs/KNOWLEDGE_REQUIREMENTS.md) | Kiến thức cần có |
| [docs/EZYDATA_COMPARISON.md](docs/EZYDATA_COMPARISON.md) | So sánh EzyData vs Hibernate |
| [docs/HIBERNATE_SPRINGBOOT_GUIDE.md](docs/HIBERNATE_SPRINGBOOT_GUIDE.md) | 🔥 Hibernate trong Spring Boot |
| [docs/IMPLEMENTATION_ROADMAP.md](docs/IMPLEMENTATION_ROADMAP.md) | 🗺️ Roadmap triển khai |

## 🚀 Quick Start

```bash
# Clone và chạy
cd mini-hibernate
./gradlew build
./gradlew test
```

## 📖 Phases

| Phase | Mô tả | Status |
|-------|-------|--------|
| 1 | Core Foundation - Annotations, Metadata | 🔲 |
| 2 | Session Management | 🔲 |
| 3 | CRUD Operations | 🔲 |
| 4 | Query API (Basic) | 🔲 |
| 5 | Transaction Management | 🔲 |
| 6 | Caching (1st Level) | 🔲 |
| 7 | Relationships (OneToMany, ManyToOne) | 🔲 |
| 8 | Lazy Loading | 🔲 |

## 🔗 References

- [Hibernate ORM Source](https://github.com/hibernate/hibernate-orm)
- [JPA Specification](https://jakarta.ee/specifications/persistence/)
- [Hibernate User Guide](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html)
