package org.ltl.minihibernate.transaction;

import org.ltl.minihibernate.api.MiniEntityManager;
import org.ltl.minihibernate.api.MiniEntityManagerFactory;
import org.ltl.minihibernate.api.MiniTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Dynamic Proxy handler that wraps methods with transaction management.
 * 
 * <pre>
 * // Create transactional proxy
 * UserService service = TransactionInterceptor.createProxy(
 *     new UserServiceImpl(), 
 *     UserService.class, 
 *     factory
 * );
 * 
 * // Calls will be wrapped in transactions
 * service.transferMoney(from, to, amount);
 * </pre>
 */
public class TransactionInterceptor implements InvocationHandler {

  private static final Logger log = LoggerFactory.getLogger(TransactionInterceptor.class);
  
  private final Object target;
  private final MiniEntityManagerFactory factory;
  private static final ThreadLocal<MiniEntityManager> currentEntityManager = new ThreadLocal<>();

  private TransactionInterceptor(Object target, MiniEntityManagerFactory factory) {
    this.target = target;
    this.factory = factory;
  }

  /**
   * Creates a transactional proxy for the given target.
   */
  @SuppressWarnings("unchecked")
  public static <T> T createProxy(T target, Class<T> interfaceType, MiniEntityManagerFactory factory) {
    return (T) Proxy.newProxyInstance(
        interfaceType.getClassLoader(),
        new Class<?>[]{interfaceType},
        new TransactionInterceptor(target, factory)
    );
  }

  /**
   * Get the current EntityManager for this thread (for use in service methods).
   */
  public static MiniEntityManager getCurrentEntityManager() {
    MiniEntityManager em = currentEntityManager.get();
    if (em == null) {
      throw new IllegalStateException("No active EntityManager. Are you inside a @Transactional method?");
    }
    return em;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // Look up the method on the target class (implementation) to find annotations
    Method targetMethod = findTargetMethod(method);
    
    // Check for @Transactional annotation on method or class
    Transactional txAnnotation = targetMethod.getAnnotation(Transactional.class);
    if (txAnnotation == null) {
      txAnnotation = target.getClass().getAnnotation(Transactional.class);
    }

    // If not transactional, just invoke directly
    if (txAnnotation == null) {
      return method.invoke(target, args);
    }

    return executeInTransaction(targetMethod, args, txAnnotation);
  }

  private Method findTargetMethod(Method interfaceMethod) {
    try {
      return target.getClass().getMethod(interfaceMethod.getName(), interfaceMethod.getParameterTypes());
    } catch (NoSuchMethodException e) {
      return interfaceMethod;
    }
  }

  private Object executeInTransaction(Method method, Object[] args, Transactional txAnnotation) throws Throwable {
    MiniEntityManager em = factory.createEntityManager();
    MiniTransaction tx = em.getTransaction();
    
    // Store in ThreadLocal for service to access
    currentEntityManager.set(em);
    
    try {
      tx.begin();
      log.debug("Transaction started for {}.{}", target.getClass().getSimpleName(), method.getName());
      
      Object result = method.invoke(target, args);
      
      tx.commit();
      log.debug("Transaction committed for {}.{}", target.getClass().getSimpleName(), method.getName());
      
      return result;
      
    } catch (Throwable e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      
      if (shouldRollback(cause, txAnnotation)) {
        if (tx.isActive()) {
          tx.rollback();
          log.warn("Transaction rolled back for {}.{} due to: {}", 
              target.getClass().getSimpleName(), method.getName(), cause.getMessage());
        }
      }
      throw cause;
      
    } finally {
      currentEntityManager.remove();
      em.close();
    }
  }

  private boolean shouldRollback(Throwable cause, Transactional txAnnotation) {
    // Check noRollbackFor first
    for (Class<? extends Throwable> noRollback : txAnnotation.noRollbackFor()) {
      if (noRollback.isAssignableFrom(cause.getClass())) {
        return false;
      }
    }

    // Check specific rollbackFor
    if (txAnnotation.rollbackFor().length > 0) {
      for (Class<? extends Throwable> rollback : txAnnotation.rollbackFor()) {
        if (rollback.isAssignableFrom(cause.getClass())) {
          return true;
        }
      }
      return false;
    }

    // Default behavior
    return txAnnotation.rollbackOnAnyException();
  }
}
