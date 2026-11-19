package com.foodloop.claim;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.foodloop.user.Role;

/**
 * The claim state machine. Each move says WHO may make it and from WHICH states; anything else is refused:
 * the wrong person with 403, the wrong state with 422. The system's own moves (expiry, 24-hour auto-complete) are
 * not here: they are made only by the jobs, never by an API caller.
 *
 * <pre>
 *   CLAIMED --MARK_READY (donor)--> READY --CONFIRM_HANDOVER (donor)--> PICKED_UP --CONFIRM_RECEIPT (receiver)--> COMPLETED
 *   CLAIMED or READY --CANCEL (donor or receiver)--> CANCELLED
 *   CLAIMED or READY --pick-up window ends (job)--> EXPIRED        PICKED_UP --24 hours (job)--> COMPLETED
 * </pre>
 */
public enum ClaimMove {

    MARK_READY(Set.of(Role.DONOR), Set.of(ClaimStatus.CLAIMED), ClaimStatus.READY,
            "Can't mark it as packed: the claim must be Claimed."),
    CONFIRM_HANDOVER(Set.of(Role.DONOR), Set.of(ClaimStatus.READY), ClaimStatus.PICKED_UP,
            "Can't confirm the handover: it isn't packed yet."),
    CONFIRM_RECEIPT(Set.of(Role.RECEIVER), Set.of(ClaimStatus.PICKED_UP), ClaimStatus.COMPLETED,
            "Can't confirm receipt: the food hasn't been handed over yet."),
    CANCEL(Set.of(Role.DONOR, Role.RECEIVER), ClaimStatus.BEFORE_PICKUP, ClaimStatus.CANCELLED,
            "Can't cancel: it's already picked up or closed.");

    private final Set<Role> roles;
    private final Set<ClaimStatus> from;
    private final ClaimStatus to;
    private final String illegalMessage;

    ClaimMove(Set<Role> roles, Set<ClaimStatus> from, ClaimStatus to, String illegalMessage) {
        this.roles = roles;
        this.from = from;
        this.to = to;
        this.illegalMessage = illegalMessage;
    }

    public boolean mayBeMadeBy(Role role) {
        return roles.contains(role);
    }

    public boolean isAllowedFrom(ClaimStatus status) {
        return from.contains(status);
    }

    public ClaimStatus target() {
        return to;
    }

    public String illegalMessage() {
        return illegalMessage;
    }

    /** What a person in this role can do to a claim in this state: the frontend shows exactly these buttons. */
    public static List<ClaimMove> available(ClaimStatus status, Role role) {
        return Arrays.stream(values()).filter(move -> move.mayBeMadeBy(role) && move.isAllowedFrom(status)).toList();
    }
}
