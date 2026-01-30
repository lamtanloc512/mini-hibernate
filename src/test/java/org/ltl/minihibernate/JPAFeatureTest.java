package org.ltl.minihibernate;

import jakarta.persistence.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ltl.minihibernate.provider.MiniPersistenceProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class JPAFeatureTest {

  private EntityManagerFactory emf;
  private EntityManager em;

  @BeforeEach
  void setUp() {
    Map<String, Object> props = new HashMap<>();
    props.put("javax.persistence.jdbc.url", "jdbc:h2:mem:jpa_test;DB_CLOSE_DELAY=-1");
    props.put("javax.persistence.jdbc.user", "sa");
    props.put("javax.persistence.jdbc.password", "");

    // Create tables manually since hbm2ddl is not fully supported logic in this
    // mini version
    // But MiniPersistenceProvider might not support auto-creation?
    // We should create tables via JDBC or if the provider supports it.
    // Provider says: generateSchema unimplemented or returns false.
    // So we need to create tables.

    // We can use a trick: create a temporary EntityPersister or just raw JDBC.
    // Or we rely on H2 INIT=...
    String initScript = "DROP TABLE IF EXISTS Employee; DROP TABLE IF EXISTS Department; " +
        "CREATE TABLE Department (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255)); " +
        "CREATE TABLE Employee (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), department_id BIGINT);";
    props.put("javax.persistence.jdbc.url", "jdbc:h2:mem:jpa_test;DB_CLOSE_DELAY=-1;INIT=" + initScript);

    MiniPersistenceProvider provider = new MiniPersistenceProvider();
    // We need to pass the classes. But createEntityManagerFactory(String, Map)
    // usually reads persistence.xml.
    // MiniPersistenceProvider.createContainerEntityManagerFactory takes
    // PersistenceUnitInfo (which has classes).
    // The current createEntityManagerFactory(String, Map) DOES NOT add classes (bug
    // in provider wrapper, or by design).
    // But MiniEntityManagerFactoryImpl.Builder DOES.
    // So we can casting or use a specific way.
    // Or we use the internal Builder directly for testing.

    // Let's rely on internal implementation details for this test or fix the
    // Provider to allow adding classes?
    // Fix: Provider doesn't allow adding classes via Map properties easily unless
    // we hack.

    // We will use MiniEntityManagerFactoryImpl.Builder directly for this test
    // suite.
    org.ltl.minihibernate.internal.MiniEntityManagerFactoryImpl.Builder builder = org.ltl.minihibernate.internal.MiniEntityManagerFactoryImpl
        .builder();

    builder.url("jdbc:h2:mem:jpa_test;DB_CLOSE_DELAY=-1");
    builder.username("sa");
    builder.addEntityClass(Department.class);
    builder.addEntityClass(Employee.class);

    emf = builder.build();
    em = emf.createEntityManager();

    // Init DB
    try {
      java.sql.Connection conn = em.unwrap(java.sql.Connection.class);
      try (java.sql.Statement stmt = conn.createStatement()) {
        stmt.execute(initScript);
      }
    } catch (Exception e) {
      throw new RuntimeException("DB Init failed", e);
    }
  }

  @AfterEach
  void tearDown() {
    if (em != null)
      em.close();
    if (emf != null)
      emf.close();
  }

  @Test
  void testRelationships() {
    em.getTransaction().begin();
    Department dep = new Department();
    dep.setName("Engineering");
    em.persist(dep);

    Employee emp = new Employee();
    emp.setName("Alice");
    emp.setDepartment(dep);
    em.persist(emp);

    em.getTransaction().commit();

    Long empId = emp.getId();
    Long depId = dep.getId();

    em.clear(); // Detach all

    Employee loadedEmp = em.find(Employee.class, empId);
    assertThat(loadedEmp).isNotNull();
    assertThat(loadedEmp.getName()).isEqualTo("Alice");
    assertThat(loadedEmp.getDepartment()).isNotNull();
    assertThat(loadedEmp.getDepartment().getName()).isEqualTo("Engineering");
    assertThat(loadedEmp.getDepartment().getId()).isEqualTo(depId);
  }

  @Test
  void testJPQL() {
    em.getTransaction().begin();
    Department dep = new Department();
    dep.setName("Sales");
    em.persist(dep);

    Employee emp1 = new Employee();
    emp1.setName("Bob");
    emp1.setDepartment(dep);
    em.persist(emp1);

    Employee emp2 = new Employee();
    emp2.setName("Charlie");
    emp2.setDepartment(dep);
    em.persist(emp2);

    em.getTransaction().commit();
    em.clear();

    TypedQuery<Employee> query = em.createQuery("SELECT e FROM Employee e WHERE e.name = :n", Employee.class);
    query.setParameter("n", "Bob");

    List<Employee> results = query.getResultList();
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getName()).isEqualTo("Bob");
    assertThat(results.get(0).getDepartment().getName()).isEqualTo("Sales");
  }

  // Entities
  @Entity
  @Table(name = "Department")
  public static class Department {
    @Id
    @GeneratedValue
    private Long id;

    private String name;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  @Entity
  @Table(name = "Employee")
  public static class Employee {
    @Id
    @GeneratedValue
    private Long id;

    private String name;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Department getDepartment() {
      return department;
    }

    public void setDepartment(Department department) {
      this.department = department;
    }
  }
}
