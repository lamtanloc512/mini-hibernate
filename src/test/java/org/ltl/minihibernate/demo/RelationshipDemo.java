package org.ltl.minihibernate.demo;

import org.ltl.minihibernate.example.Order;
import org.ltl.minihibernate.example.User;
import org.ltl.minihibernate.metadata.EntityMetadata;
import org.ltl.minihibernate.metadata.FieldMetadata;
import org.ltl.minihibernate.metadata.MetadataParser;

/**
 * Demo: Relationship annotations parsing.
 */
public class RelationshipDemo {

  public static void main(String[] args) {
    System.out.println("=== Relationship Annotations Demo ===\n");

    MetadataParser parser = new MetadataParser();

    // Parse User entity
    EntityMetadata userMetadata = parser.parse(User.class);
    System.out.println("User entity:");
    System.out.println("  Table: " + userMetadata.getTableName());
    System.out.println("  Columns: " + userMetadata.getAllColumns().size());
    System.out.println("  Relationships: " + userMetadata.getRelationships().size());

    System.out.println();

    // Parse Order entity with @ManyToOne
    EntityMetadata orderMetadata = parser.parse(Order.class);
    System.out.println("Order entity:");
    System.out.println("  Table: " + orderMetadata.getTableName());
    System.out.println("  Columns: " + orderMetadata.getAllColumns().size());
    System.out.println("  Relationships: " + orderMetadata.getRelationships().size());

    System.out.println("\n  @ManyToOne relationships:");
    for (FieldMetadata rel : orderMetadata.getManyToOneRelationships()) {
      System.out.println("    - " + rel.getFieldName() + " -> " + rel.getTargetEntity().getSimpleName());
      System.out.println("      FK column: " + rel.getColumnName());
    }

    System.out.println("\n✅ Relationship parsing demo complete!");
  }
}
