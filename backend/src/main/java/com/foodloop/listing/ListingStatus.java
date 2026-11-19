package com.foodloop.listing;

import java.util.Set;

/**
 * A listing's status mirrors its claim while one is active: LISTED, then CLAIMED, READY, PICKED_UP and COMPLETED.
 * EXPIRED (the pick-up window ended without a hand-over) and WITHDRAWN (the donor or an Admin took it down) are final.
 * A cancelled claim sends the listing back to LISTED while its window is open.
 */
public enum ListingStatus {
    LISTED,
    CLAIMED,
    READY,
    PICKED_UP,
    COMPLETED,
    EXPIRED,
    WITHDRAWN;

    /** The three final states: shown under "Done today" and History. */
    public static final Set<ListingStatus> CLOSED = Set.of(COMPLETED, EXPIRED, WITHDRAWN);
}
