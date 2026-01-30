# Cấu Trúc Package Mini-Hibernate

Tài liệu này mô tả chức năng của từng package trong dự án `mini-hibernate`, giúp lập trình viên hiểu rõ tổ chức mã nguồn của thư viện.

Các package gốc nằm tại: `org.ltl.minihibernate`

## 1. Core Runtime (`org.ltl.minihibernate.internal`)

Đây là nơi chứa các mã nguồn triển khai (implementation) chính của chuẩn JPA.

- **Chức năng**: Hiện thực hóa các interface của `jakarta.persistence` (như `EntityManager`, `EntityManagerFactory`, `Query`).
- **Thành phần chính**:
  - `MiniEntityManagerImpl`: Quản lý vòng đời Entity.
  - `MiniNativeQueryImpl`: Xử lý Native SQL.
  - `metamodel/*`: Các class mô tả cấu trúc Entity (đã giải thích chi tiết ở tài liệu Architecture).

## 2. Provider Entry Point (`org.ltl.minihibernate.provider`)

- **Chức năng**: Cổng giao tiếp với thế giới bên ngoài (Java SPI).
- **Thành phần chính**:
  - `MiniPersistenceProvider`: Được gọi bởi `Persistence.createEntityManagerFactory`. Nó khởi động toàn bộ hệ thống.

## 3. Persistence Engine (`org.ltl.minihibernate.persist`)

- **Chức năng**: "Công nhân" thực hiện các thao tác mức thấp với Database và quản lý bộ nhớ đệm.
- **Thành phần chính**:
  - `EntityPersister`: Chịu trách nhiệm sinh lệnh INSERT/UPDATE/DELETE và gọi JDBC.
  - `PersistenceContext`: Bộ nhớ L1 Cache và Unit of Work logic.

## 4. Metadata Mapping (`org.ltl.minihibernate.metadata`)

- **Chức năng**: Đọc hiểu các Annotation của người dùng (`@Entity`, `@Table`, `@Column`...).
- **Thành phần chính**:
  - `EntityMetadata`: Object chứa thông tin ánh xạ của một bảng (tên bảng, tên cột, khóa chính...).

## 5. SQL Generation (`org.ltl.minihibernate.sql`)

- **Chức năng**: Sinh mã SQL động.
- **Thành phần chính**:
  - `SQLGenerator`: Class tiện ích để nối chuỗi tạo thành câu SQL hoàn chỉnh dựa trên Metadata.

## 6. Public API (`org.ltl.minihibernate.api`)

- **Chức năng**: Các Interface mở rộng riêng của thư viện, nằm ngoài chuẩn JPA.
- **Thành phần chính**:
  - `MiniEntityManager`: Interface con của `jakarta.persistence.EntityManager`, có thể thêm các method tiện ích riêng.

## 7. Repository Support (`org.ltl.minihibernate.repository`)

- **Chức năng**: Hỗ trợ mô hình Repository (giống Spring Data JPA nhưng ở mức đơn giản).
- **Thành phần chính**:
  - `MiniRepository`: Interface cơ sở cho các Repo.
  - `RepositoryFactory`: Factory dùng Dynamic Proxy để tạo implementation cho các Interface Repository mà người dùng khai báo.

## 8. Query Engine (`org.ltl.minihibernate.query`)

- **Chức năng**: Xử lý câu truy vấn (không phải Native SQL).
- **Thành phần chính**:
  - `SimpleJPQLParser`: Bộ phân tích cú pháp đơn giản cho JPQL (Java Persistence Query Language).
  - `MiniQuery`: Class đại diện cho câu truy vấn tùy chỉnh.

## 9. Pagination (`org.ltl.minihibernate.page`)

- **Chức năng**: Hỗ trợ phân trang dữ liệu.
- **Thành phần chính**:
  - `Page`, `Pageable`, `Sort`: Các đối tượng chứa thông tin trang (số trang, kích thước) và kết quả trả về.

## 10. Legacy / Alternative Session (`org.ltl.minihibernate.session`)

- **Chức năng**: Cung cấp API kiểu Hibernate "Session" (thay vì kiểu JPA "EntityManager"). Có thể là code cũ hoặc một cách tiếp cận thay thế.
- **Thành phần chính**:
  - `MiniSession`, `MiniSessionFactory`: Tương tự như EntityManager nhưng theo phong cách Hibernate thuần.

## 11. Transaction (`org.ltl.minihibernate.transaction`)

- **Chức năng**: Quản lý giao dịch Database.
- **Thành phần chính**:
  - `MiniTransaction`: Wrapper xung quanh `Connection.commit()` và `Connection.rollback()`.

## 12. Utilities & Misc

- `org.ltl.minihibernate.annotation`: Chứa các custom annotation (ví dụ `@Query` riêng nếu không dùng chuẩn JPA).
- `org.ltl.minihibernate.config`: Chứa logic load file cấu hình (ví dụ `minihibernate.properties`).
- `org.ltl.minihibernate.util`: Các hàm tiện ích chung (Reflection utils, String utils).
- `org.ltl.minihibernate.spi`: Các interface nội bộ dùng để mở rộng hệ thống (như ConnectionProvider).
