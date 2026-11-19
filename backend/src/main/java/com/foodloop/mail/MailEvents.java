package com.foodloop.mail;

/**
 * Things that happened and should be told by email. Services publish them inside their transaction;
 * {@link MailEventListener} sends the email only after the transaction has committed, so a rolled-back move never
 * sends anything and no database lock is held while talking to the SMTP server. The jobs publish them outside a
 * transaction (each claim is its own committed UPDATE), which the listener also handles.
 */
public final class MailEvents {

    private MailEvents() {
    }

    /** A receiver won a claim: "new claim" to the donor and "claim confirmation" to the receiver. */
    public record ClaimCreated(Long claimId) {
    }

    public record ClaimReady(Long claimId) {
    }

    /** The donor confirmed the hand-over: "picked up" to both. */
    public record ClaimPickedUp(Long claimId) {
    }

    /** The receiver confirmed receipt, or 24 hours passed: "completed" to both. */
    public record ClaimCompleted(Long claimId) {
    }

    /** {@code notifyUserId} is the side that did not cancel (or, after a suspension, the side that was not suspended). */
    public record ClaimCancelled(Long claimId, Long notifyUserId) {
    }

    /** The pick-up window ended with the claim not collected: both sides are told. */
    public record ClaimExpired(Long claimId) {
    }

    /** The pick-up window ended and nobody had claimed the listing: the donor is told. */
    public record ListingExpired(Long listingId) {
    }

    public record DonorVerified(Long userId) {
    }

    public record DonorNotVerified(Long userId) {
    }

    /**
     * Carries the reason itself: the email is built after the commit, and an Admin who reinstates the account a moment
     * later would otherwise clear the reason before the email reads it.
     */
    public record AccountSuspended(Long userId, String reason) {
    }

    public record AccountReinstated(Long userId) {
    }
}
