package com.foodloop.claim;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * One receiver's winning claim on one listing. The legal moves and who may make them are in
 * {@link ClaimStateMachine}; this class only records them. The claim number people see is "FL-" + (id + 2000).
 */
@Entity
@Table(name = "claims")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ClaimStatus status;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "auto_completed", nullable = false)
    private boolean autoCompleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Claim() {
    }

    public Claim(Long listingId, Long receiverId, Instant claimedAt) {
        this.listingId = listingId;
        this.receiverId = receiverId;
        this.claimedAt = claimedAt;
        this.status = ClaimStatus.CLAIMED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** "FL-2041". Computed from the id, so it is unique without another column. */
    public static String numberFor(Long claimId) {
        return "FL-" + (claimId + 2000);
    }

    public String number() {
        return numberFor(id);
    }

    public void markReady(Instant now) {
        status = ClaimStatus.READY;
        readyAt = now;
    }

    public void markPickedUp(Instant now) {
        status = ClaimStatus.PICKED_UP;
        pickedUpAt = now;
    }

    public void markCompleted(Instant now) {
        status = ClaimStatus.COMPLETED;
        completedAt = now;
    }

    /** Used by the seed only: the expiry job closes claims with a conditional UPDATE instead. */
    public void markExpired(Instant now) {
        status = ClaimStatus.EXPIRED;
        expiredAt = now;
    }

    public void markCancelled(Long byUserId, Instant now) {
        status = ClaimStatus.CANCELLED;
        cancelledBy = byUserId;
        cancelledAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getListingId() {
        return listingId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public Instant getPickedUpAt() {
        return pickedUpAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Long getCancelledBy() {
        return cancelledBy;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }

    public boolean isAutoCompleted() {
        return autoCompleted;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
