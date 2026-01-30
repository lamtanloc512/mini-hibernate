package org.ltl.minihibernate.internal.metamodel;

import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.CollectionAttribute;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.MapAttribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import java.util.Set;
import io.vavr.collection.HashSet;

public abstract class MiniManagedTypeImpl<X> extends MiniTypeImpl<X> implements ManagedType<X> {

  private io.vavr.collection.Set<Attribute<? super X, ?>> attributes = HashSet.empty();

  public MiniManagedTypeImpl(Class<X> javaType) {
    super(javaType);
  }

  protected void addAttribute(Attribute<? super X, ?> attribute) {
    attributes = attributes.add(attribute);
  }

  @Override
  public Set<Attribute<? super X, ?>> getAttributes() {
    return attributes.toJavaSet();
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Set<Attribute<X, ?>> getDeclaredAttributes() {
    return (Set) getAttributes();
  }

  @SuppressWarnings("unchecked")
  @Override
  public <Y> SingularAttribute<? super X, Y> getSingularAttribute(String name, Class<Y> type) {
    return (SingularAttribute<? super X, Y>) getAttribute(name);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <Y> SingularAttribute<X, Y> getDeclaredSingularAttribute(String name, Class<Y> type) {
    return (SingularAttribute<X, Y>) getSingularAttribute(name, type);
  }

  @Override
  public SingularAttribute<? super X, ?> getSingularAttribute(String name) {
    return (SingularAttribute<? super X, ?>) getAttribute(name);
  }

  @Override
  public SingularAttribute<X, ?> getDeclaredSingularAttribute(String name) {
    return (SingularAttribute<X, ?>) getDeclaredAttribute(name);
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Set<SingularAttribute<? super X, ?>> getSingularAttributes() {
    return (Set) attributes.filter(a -> !a.isCollection())
        .map(a -> (SingularAttribute<? super X, ?>) a)
        .toJavaSet();
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Set<SingularAttribute<X, ?>> getDeclaredSingularAttributes() {
    return (Set) attributes.filter(a -> !a.isCollection())
        .map(a -> (SingularAttribute<X, ?>) a)
        .toJavaSet();
  }

  @Override
  public Attribute<? super X, ?> getAttribute(String name) {
    return attributes.find(a -> a.getName().equals(name))
        .getOrElseThrow(() -> new IllegalArgumentException("Attribute not found: " + name));
  }

  @SuppressWarnings("unchecked")
  @Override
  public Attribute<X, ?> getDeclaredAttribute(String name) {
    return (Attribute<X, ?>) getAttribute(name);
  }

  // Unsupported collection attributes for now
  @Override
  public <E> CollectionAttribute<? super X, E> getCollection(String name, Class<E> elementType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <E> CollectionAttribute<X, E> getDeclaredCollection(String name, Class<E> elementType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <E> SetAttribute<? super X, E> getSet(String name, Class<E> elementType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <E> SetAttribute<X, E> getDeclaredSet(String name, Class<E> elementType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <E> ListAttribute<? super X, E> getList(String name, Class<E> elementType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <E> ListAttribute<X, E> getDeclaredList(String name, Class<E> elementType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <K, V> MapAttribute<? super X, K, V> getMap(String name, Class<K> keyType, Class<V> valueType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <K, V> MapAttribute<X, K, V> getDeclaredMap(String name, Class<K> keyType, Class<V> valueType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CollectionAttribute<? super X, ?> getCollection(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CollectionAttribute<X, ?> getDeclaredCollection(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SetAttribute<? super X, ?> getSet(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SetAttribute<X, ?> getDeclaredSet(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ListAttribute<? super X, ?> getList(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ListAttribute<X, ?> getDeclaredList(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MapAttribute<? super X, ?, ?> getMap(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MapAttribute<X, ?, ?> getDeclaredMap(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<PluralAttribute<? super X, ?, ?>> getPluralAttributes() {
    return java.util.Collections.emptySet();
  }

  @Override
  public Set<PluralAttribute<X, ?, ?>> getDeclaredPluralAttributes() {
    return java.util.Collections.emptySet();
  }
}
