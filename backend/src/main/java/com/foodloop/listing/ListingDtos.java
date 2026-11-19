package com.foodloop.listing;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.foodloop.claim.ClaimMove;
import com.foodloop.claim.ClaimStatus;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies for listings. Messages match UI-UX.md (Validation messages). */
public final class ListingDtos {

    private ListingDtos() {
    }

    /** Create or edit. The photo is sent separately (PUT /api/donor/listings/{id}/photo). Times are UTC instants. */
    public record ListingRequest(
            @NotBlank(message = "Say what the food is.") @Size(max = 100, message = "Use at most 100 characters.") String title,
            @NotBlank(message = "Enter how much there is, like \"10 pieces\".") @Size(max = 60, message = "Use at most 60 characters.") String quantity,
            @NotNull(message = "Pick a category.") Category category,
            Set<Dietary> dietary,
            @NotNull(message = "Choose when pick-up starts.") Instant pickupFrom,
            @NotNull(message = "Choose when pick-up ends.") Instant pickupUntil,
            @NotNull(message = "Choose the best-before time.") Instant bestBefore,
            @AssertTrue(message = "Confirm the food is safe to eat before publishing.") boolean safetyConfirmed) {
    }

    /** The donor behind a listing, as receivers see it. Always verified (only verified donors can list). */
    public record DonorBrief(String businessName, boolean verified) {
    }

    /**
     * One ticket in Find food. The full address is not here: receivers get it only on their own claim.
     * approxLat and approxLng place the listing on the Find food map, rounded to 2 decimals (about 1 km),
     * so the map shows the neighbourhood and never the donor's exact door.
     */
    public record ListingCard(
            Long id,
            String title,
            String quantity,
            Category category,
            Set<Dietary> dietary,
            DonorBrief donor,
            String area,
            /** Kilometres from the search point; null when no location is known (a listing opened without one). */
            Double distanceKm,
            Instant pickupFrom,
            Instant pickupUntil,
            Instant bestBefore,
            String timeZone,
            boolean hasPhoto,
            Instant createdAt,
            double approxLat,
            double approxLng) {

        /** Two decimals of a degree: about 1.1 km north–south, less east–west. */
        public static double approx(double degrees) {
            return Math.round(degrees * 100.0) / 100.0;
        }
    }

    /** The listing page for a receiver: the card plus what this receiver can do with it. */
    public record ListingDetail(
            ListingCard listing,
            ListingStatus status,
            /** The receiver's own claim on this listing, if any (so the page links to it instead of "Claim"). */
            Long myClaimId,
            /** True when this receiver already reported the listing. */
            boolean reported) {
    }

    /**
     * The claim shown on the donor's board card and listing view. {@code allowedActions} are the moves the donor can
     * make now (from ClaimMove), so the board shows the same buttons as the claim view.
     */
    public record ClaimBrief(Long id, String number, ClaimStatus status, String receiverName, List<ClaimMove> allowedActions) {
    }

    /** A listing as its own donor sees it: every field, the full address and the current or last claim. */
    public record DonorListing(
            Long id,
            String title,
            String quantity,
            Category category,
            Set<Dietary> dietary,
            Instant pickupFrom,
            Instant pickupUntil,
            Instant bestBefore,
            String pickupAddress,
            String pickupArea,
            String timeZone,
            ListingStatus status,
            boolean hasPhoto,
            ClaimBrief claim,
            Instant closedAt,
            Instant createdAt) {
    }
}
