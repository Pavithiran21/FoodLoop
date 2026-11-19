package com.foodloop.user;

import org.springframework.http.HttpStatus;

import com.foodloop.common.error.ApiException;

/**
 * The person making the request, loaded fresh from the database for every request (see
 * {@link CurrentUserResolver}). Services receive it as a parameter and check the role themselves.
 */
public record CurrentUser(Long id, String name, Role role, boolean demo) {

    public boolean isDonor() {
        return role == Role.DONOR;
    }

    public boolean isReceiver() {
        return role == Role.RECEIVER;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Role checks in the service layer: the first line of every role-specific operation. */
    public void requireDonor() {
        if (!isDonor()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This is for donors only.");
        }
    }

    public void requireReceiver() {
        if (!isReceiver()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This is for receivers only.");
        }
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This is for admins only.");
        }
    }
}
