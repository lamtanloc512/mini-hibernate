package org.ltl.minihibernate.example;

import jakarta.persistence.*;

/**
 * Order entity demonstrating @ManyToOne relationship.
 * Many orders belong to one user.
 */
@Entity
@Table(name = "orders")
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "product_name", nullable = false)
  private String productName;

  @Column(name = "quantity")
  private Integer quantity;

  @Column(name = "price")
  private Double price;

  /**
   * Many-to-One relationship: Many orders belong to one user.
   * The foreign key column is "user_id" in the orders table.
   */
  @ManyToOne
  @JoinColumn(name = "user_id")
  private User user;

  public Order() {
  }

  public Order(String productName, Integer quantity, Double price) {
    this.productName = productName;
    this.quantity = quantity;
    this.price = price;
  }

  // Getters and Setters
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getProductName() { return productName; }
  public void setProductName(String productName) { this.productName = productName; }

  public Integer getQuantity() { return quantity; }
  public void setQuantity(Integer quantity) { this.quantity = quantity; }

  public Double getPrice() { return price; }
  public void setPrice(Double price) { this.price = price; }

  public User getUser() { return user; }
  public void setUser(User user) { this.user = user; }

  @Override
  public String toString() {
    return "Order{id=" + id + ", productName='" + productName + "', quantity=" + quantity + 
        ", price=" + price + ", userId=" + (user != null ? user.getId() : null) + "}";
  }
}
