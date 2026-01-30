package org.ltl.minihibernate.internal.metamodel;

import jakarta.persistence.metamodel.Bindable;
import jakarta.persistence.metamodel.EntityType;

public class MiniEntityTypeImpl<X> extends MiniIdentifiableTypeImpl<X> implements EntityType<X> {

  private final String name;

  public MiniEntityTypeImpl(Class<X> javaType, String name) {
    super(javaType);
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public BindableType getBindableType() {
    return Bindable.BindableType.ENTITY_TYPE;
  }

  @Override
  public Class<X> getBindableJavaType() {
    return getJavaType();
  }

  @Override
  public PersistenceType getPersistenceType() {
    return PersistenceType.ENTITY;
  }
}
