package com.foodloop.claim;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.foodloop.listing.Category;
import com.foodloop.listing.Dietary;
import com.foodloop.listing.ListingStatus;
import com.foodloop.user.Role;

/** Response bodies for claims. */
public final class ClaimDtos {

    private ClaimDtos() {
    }

    public record ListingSummary(
            Long id,
            String title,
            String quantity,
            Category category,
            Set<Dietary> dietary,
            Instant pickupFrom,
            Instant pickupUntil,
            Instant bestBefore,
            String timeZone,
            boolean hasPhoto,
            ListingStatus status,
            /** When the donor listed it: the first step of the claim tracker. */
            Instant createdAt) {
    }

    /** The pick-up place. Only the claimant and the donor get it; everyone else sees just the listing's area. */
    public record Pickup(String address, String area, double lat, double lng) {
    }

    /** Who ended the claim: their role, and whether it was the person looking at it. */
    public record CancelledBy(Role role, boolean you) {
    }

    /**
     * One claim as the donor or the receiver sees it. {@code allowedActions} lists the moves this viewer can make now
     * (the buttons to show); {@code reported} is true when this viewer already reported: the donor the claim's
     * receiver, or the receiver the listing (from the listing page or the claim view).
     */
    public record ClaimResponse(
            Long id,
            String number,
            ClaimStatus status,
            Role viewerRole,
            ListingSummary listing,
            String donorName,
            String receiverName,
            Pickup pickup,
            Instant claimedAt,
            Instant readyAt,
            Instant pickedUpAt,
            Instant completedAt,
            boolean autoCompleted,
            Instant cancelledAt,
            CancelledBy cancelledBy,
            Instant expiredAt,
            List<ClaimMove> allowedActions,
            boolean reported) {
    }

    /** One row in My claims (receiver): enough to show the row and open the claim. */
    public record ClaimRow(
            Long id,
            String number,
            ClaimStatus status,
            Long listingId,
            String title,
            String quantity,
            Category category,
            boolean hasPhoto,
            String donorName,
            Instant pickupFrom,
            Instant pickupUntil,
            String timeZone,
            Instant updatedAt) {
    }

    public record ActiveCount(long active) {
    }
}
