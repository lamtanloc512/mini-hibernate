package org.ltl.minihibernate.internal.metamodel;

import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import java.lang.reflect.Member;

public abstract class MiniAttributeImpl<X, Y> implements Attribute<X, Y> {

  private final String name;
  private final Class<Y> javaType;
  private final ManagedType<X> declaringType;
  private final Member member;
  private final PersistentAttributeType persistentAttributeType;

  protected MiniAttributeImpl(String name, Class<Y> javaType, ManagedType<X> declaringType, Member member,
      PersistentAttributeType persistentAttributeType) {
    this.name = name;
    this.javaType = javaType;
    this.declaringType = declaringType;
    this.member = member;
    this.persistentAttributeType = persistentAttributeType;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public PersistentAttributeType getPersistentAttributeType() {
    return persistentAttributeType;
  }

  @Override
  public ManagedType<X> getDeclaringType() {
    return declaringType;
  }

  @Override
  public Class<Y> getJavaType() {
    return javaType;
  }

  @Override
  public Member getJavaMember() {
    return member;
  }

  @Override
  public boolean isAssociation() {
    return persistentAttributeType == PersistentAttributeType.MANY_TO_ONE ||
        persistentAttributeType == PersistentAttributeType.ONE_TO_MANY ||
        persistentAttributeType == PersistentAttributeType.ONE_TO_ONE ||
        persistentAttributeType == PersistentAttributeType.MANY_TO_MANY;
  }

  @Override
  public boolean isCollection() {
    return false;
  }
}
