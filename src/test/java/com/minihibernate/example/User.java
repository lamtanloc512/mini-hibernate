package com.minihibernate.example;

import com.minihibernate.annotation.Column;
import com.minihibernate.annotation.Entity;
import com.minihibernate.annotation.GeneratedValue;
import com.minihibernate.annotation.Id;

/**
 * Example entity for testing and learning.
 */
@Entity(table = "users")
public class User {
    
    @Id
    @GeneratedValue
    private Long id;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(name = "age")
    private Integer age;
    
    // Default constructor required for ORM
    public User() {
    }
    
    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }
    
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
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public Integer getAge() {
        return age;
    }
    
    public void setAge(Integer age) {
        this.age = age;
    }
    
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", age=" + age +
                '}';
    }
}
