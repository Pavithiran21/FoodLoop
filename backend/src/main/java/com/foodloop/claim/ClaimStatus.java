package com.foodloop.claim;

import java.util.Set;

/**
 * CLAIMED, READY and PICKED_UP are the "active" states: while a claim is in one of them its listing is taken.
 * COMPLETED, CANCELLED and EXPIRED are final. The legal moves are in {@link ClaimStateMachine}.
 */
public enum ClaimStatus {
    CLAIMED,
    READY,
    PICKED_UP,
    COMPLETED,
    CANCELLED,
    EXPIRED;

    /** Matches the generated column claims.active_listing_id in V1__init.sql. */
    public static final Set<ClaimStatus> ACTIVE = Set.of(CLAIMED, READY, PICKED_UP);
    public static final Set<ClaimStatus> PAST = Set.of(COMPLETED, CANCELLED, EXPIRED);
    /** Before the hand-over: either side may still cancel, and the expiry job may still end the claim. */
    public static final Set<ClaimStatus> BEFORE_PICKUP = Set.of(CLAIMED, READY);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
