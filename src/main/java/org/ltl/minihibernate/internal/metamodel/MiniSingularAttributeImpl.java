package org.ltl.minihibernate.internal.metamodel;

import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import java.lang.reflect.Member;

public class MiniSingularAttributeImpl<X, T> extends MiniAttributeImpl<X, T> implements SingularAttribute<X, T> {

  private final boolean isId;
  private final boolean isVersion;
  private final boolean isOptional;
  private final Type<T> type;

  public MiniSingularAttributeImpl(String name, Class<T> javaType, ManagedType<X> declaringType, Member member,
      PersistentAttributeType persistentAttributeType, boolean isId, boolean isVersion, boolean isOptional,
      Type<T> type) {
    super(name, javaType, declaringType, member, persistentAttributeType);
    this.isId = isId;
    this.isVersion = isVersion;
    this.isOptional = isOptional;
    this.type = type;
  }

  @Override
  public boolean isId() {
    return isId;
  }

  @Override
  public boolean isVersion() {
    return isVersion;
  }

  @Override
  public boolean isOptional() {
    return isOptional;
  }

  @Override
  public Type<T> getType() {
    return type;
  }

  @Override
  public BindableType getBindableType() {
    return BindableType.SINGULAR_ATTRIBUTE;
  }

  @Override
  public Class<T> getBindableJavaType() {
    return getJavaType();
  }
}
