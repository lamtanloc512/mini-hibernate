package org.ltl.minihibernate.internal.metamodel;

import jakarta.persistence.metamodel.Type;
import io.vavr.control.Option;

public abstract class MiniTypeImpl<X> implements Type<X> {

  private final Class<X> javaType;

  protected MiniTypeImpl(Class<X> javaType) {
    this.javaType = javaType;
  }

  @Override
  public Class<X> getJavaType() {
    return javaType;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + Option.of(javaType).map(Class::getName).getOrElse("null") + "]";
  }
}
