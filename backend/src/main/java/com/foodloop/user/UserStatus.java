package com.foodloop.user;

/**
 * ACTIVE can log in. SUSPENDED was suspended by an Admin (the reason is shown at log-in; an Admin can reinstate).
 * DELETED deleted their own account: the row is anonymised and hidden, and the other side's records show
 * "Former user" or "Former donor".
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DELETED
}
