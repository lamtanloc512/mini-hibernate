package com.minihibernate.metadata;

import com.minihibernate.example.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for MetadataParser.
 * 
 * These tests verify that the parser correctly extracts
 * entity and field metadata from annotated classes.
 */
class MetadataParserTest {
    
    private MetadataParser parser;
    
    @BeforeEach
    void setUp() {
        parser = new MetadataParser();
    }
    
    @Test
    @DisplayName("Should parse @Entity and extract table name")
    void shouldParseEntityAnnotation() {
        // When
        EntityMetadata metadata = parser.parse(User.class);
        
        // Then
        assertThat(metadata.getEntityClass()).isEqualTo(User.class);
        assertThat(metadata.getTableName()).isEqualTo("users");
    }
    
    @Test
    @DisplayName("Should parse @Id field")
    void shouldParseIdField() {
        // When
        EntityMetadata metadata = parser.parse(User.class);
        
        // Then
        FieldMetadata idField = metadata.getIdField();
        assertThat(idField).isNotNull();
        assertThat(idField.getFieldName()).isEqualTo("id");
        assertThat(idField.isId()).isTrue();
        assertThat(idField.isGeneratedValue()).isTrue();
        assertThat(idField.getJavaType()).isEqualTo(Long.class);
    }
    
    @Test
    @DisplayName("Should parse @Column with custom name")
    void shouldParseColumnWithCustomName() {
        // When
        EntityMetadata metadata = parser.parse(User.class);
        
        // Then
        FieldMetadata nameField = metadata.getColumns().stream()
                .filter(f -> f.getFieldName().equals("name"))
                .findFirst()
                .orElseThrow();
        
        assertThat(nameField.getColumnName()).isEqualTo("user_name");
        assertThat(nameField.isNullable()).isFalse();
        assertThat(nameField.getLength()).isEqualTo(100);
    }
    
    @Test
    @DisplayName("Should parse @Column with unique constraint")
    void shouldParseColumnWithUniqueConstraint() {
        // When
        EntityMetadata metadata = parser.parse(User.class);
        
        // Then
        FieldMetadata emailField = metadata.getColumns().stream()
                .filter(f -> f.getFieldName().equals("email"))
                .findFirst()
                .orElseThrow();
        
        assertThat(emailField.isUnique()).isTrue();
        assertThat(emailField.isNullable()).isFalse();
    }
    
    @Test
    @DisplayName("Should get and set field values using reflection")
    void shouldGetAndSetFieldValues() {
        // Given
        EntityMetadata metadata = parser.parse(User.class);
        User user = new User("John", "john@example.com");
        
        // When & Then - Get value
        FieldMetadata nameField = metadata.getColumns().stream()
                .filter(f -> f.getFieldName().equals("name"))
                .findFirst()
                .orElseThrow();
        
        assertThat(nameField.getValue(user)).isEqualTo("John");
        
        // When & Then - Set value
        nameField.setValue(user, "Jane");
        assertThat(user.getName()).isEqualTo("Jane");
    }
    
    @Test
    @DisplayName("Should create new entity instance")
    void shouldCreateNewEntityInstance() {
        // Given
        EntityMetadata metadata = parser.parse(User.class);
        
        // When
        Object instance = metadata.newInstance();
        
        // Then
        assertThat(instance).isInstanceOf(User.class);
    }
    
    @Test
    @DisplayName("Should cache parsed metadata")
    void shouldCacheParsedMetadata() {
        // When
        EntityMetadata first = parser.parse(User.class);
        EntityMetadata second = parser.parse(User.class);
        
        // Then - same instance (cached)
        assertThat(first).isSameAs(second);
    }
    
    @Test
    @DisplayName("Should throw exception for non-entity class")
    void shouldThrowExceptionForNonEntityClass() {
        // When & Then
        assertThatThrownBy(() -> parser.parse(String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not annotated with @Entity");
    }
}
