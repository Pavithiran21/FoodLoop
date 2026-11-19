package com.foodloop.donor;

/**
 * Where a donor stands with the Admins. WAITING: new, or asked for review again. VERIFIED: can list food.
 * NOT_VERIFIED: an Admin refused, with a reason; the donor may fix their details and ask again.
 */
public enum VerificationStatus {
    WAITING,
    VERIFIED,
    NOT_VERIFIED
}
