package org.ltl.minihibernate.session;

/**
 * Entity states in the persistence lifecycle.
 */
public enum EntityState {

  /**
   * Entity is newly created and not associated with any session.
   * No database identity.
   */
  TRANSIENT,

  /**
   * Entity is associated with a session and tracked.
   * Changes will be synchronized on flush.
   */
  MANAGED,

  /**
   * Entity was previously managed but session is closed.
   * Has database identity but not tracked.
   */
  DETACHED,

  /**
   * Entity is marked for deletion.
   * Will be deleted on flush.
   */
  REMOVED
}
