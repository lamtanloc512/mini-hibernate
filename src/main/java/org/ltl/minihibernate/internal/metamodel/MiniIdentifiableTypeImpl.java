package org.ltl.minihibernate.internal.metamodel;

import jakarta.persistence.metamodel.IdentifiableType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import java.util.Set;
import io.vavr.control.Option;

public abstract class MiniIdentifiableTypeImpl<X> extends MiniManagedTypeImpl<X> implements IdentifiableType<X> {

  private SingularAttribute<? super X, ?> idAttribute;
  private Type<?> idType;

  public MiniIdentifiableTypeImpl(Class<X> javaType) {
    super(javaType);
  }

  public void setIdAttribute(SingularAttribute<? super X, ?> idAttribute) {
    this.idAttribute = idAttribute;
    this.idType = idAttribute.getType();
    addAttribute(idAttribute);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <Y> SingularAttribute<? super X, Y> getId(Class<Y> type) {
    return Option.of(idAttribute)
        .map(attr -> (SingularAttribute<? super X, Y>) attr)
        .getOrElseThrow(() -> new IllegalArgumentException("Id attribute not found or not initialized"));
  }

  @SuppressWarnings("unchecked")
  @Override
  public <Y> SingularAttribute<X, Y> getDeclaredId(Class<Y> type) {
    return (SingularAttribute<X, Y>) getId(type);
  }

  @Override
  public <Y> SingularAttribute<? super X, Y> getVersion(Class<Y> type) {
    throw new IllegalArgumentException("Version attribute not present");
  }

  @Override
  public <Y> SingularAttribute<X, Y> getDeclaredVersion(Class<Y> type) {
    throw new IllegalArgumentException("Version attribute not present");
  }

  @Override
  public IdentifiableType<? super X> getSupertype() {
    return null; // Inheritance not supported properly yet
  }

  @Override
  public boolean hasSingleIdAttribute() {
    return true; // We only support single ID for now
  }

  @Override
  public boolean hasVersionAttribute() {
    return false;
  }

  @Override
  public Set<SingularAttribute<? super X, ?>> getIdClassAttributes() {
    return java.util.Collections.emptySet();
  }

  @Override
  public Type<?> getIdType() {
    return idType;
  }
}
