package com.foodloop.listing;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Surplus food a donor offers. The pick-up place is copied from the donor's profile when the listing is made, so a
 * later change of the donor's address affects new listings only. The photo bytes are in {@link ListingPhoto}.
 */
@Entity
@Table(name = "listings")
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "donor_id", nullable = false)
    private Long donorId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 60)
    private String quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Category category;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "listing_dietary", joinColumns = @JoinColumn(name = "listing_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", length = 16)
    @BatchSize(size = 100)
    private Set<Dietary> dietary = new HashSet<>();

    @Column(name = "pickup_from", nullable = false)
    private Instant pickupFrom;

    @Column(name = "pickup_until", nullable = false)
    private Instant pickupUntil;

    @Column(name = "best_before", nullable = false)
    private Instant bestBefore;

    @Column(name = "pickup_address", nullable = false, length = 250)
    private String pickupAddress;

    @Column(name = "pickup_area", nullable = false, length = 80)
    private String pickupArea;

    @Column(name = "pickup_lat", nullable = false)
    private double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private double pickupLng;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ListingStatus status;

    @Column(name = "has_photo", nullable = false)
    private boolean hasPhoto;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Listing() {
    }

    public Listing(Long donorId, String title, String quantity, Category category, Set<Dietary> dietary, Instant pickupFrom,
            Instant pickupUntil, Instant bestBefore, String pickupAddress, String pickupArea, double pickupLat,
            double pickupLng, String timeZone) {
        this.donorId = donorId;
        this.status = ListingStatus.LISTED;
        this.pickupAddress = pickupAddress;
        this.pickupArea = pickupArea;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.timeZone = timeZone;
        edit(title, quantity, category, dietary, pickupFrom, pickupUntil, bestBefore);
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

    /** The fields a donor may change (only while nobody has claimed the listing). The pick-up place stays. */
    public void edit(String title, String quantity, Category category, Set<Dietary> dietary, Instant pickupFrom,
            Instant pickupUntil, Instant bestBefore) {
        this.title = title;
        this.quantity = quantity;
        this.category = category;
        this.dietary.clear();
        if (dietary != null) {
            this.dietary.addAll(dietary);
        }
        this.pickupFrom = pickupFrom;
        this.pickupUntil = pickupUntil;
        this.bestBefore = bestBefore;
    }

    /** Moves to a state and stamps closedAt when it is a final one. */
    public void moveTo(ListingStatus next, Instant now) {
        this.status = next;
        this.closedAt = ListingStatus.CLOSED.contains(next) ? now : null;
    }

    public boolean windowEnded(Instant now) {
        return !pickupUntil.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Long getDonorId() {
        return donorId;
    }

    public String getTitle() {
        return title;
    }

    public String getQuantity() {
        return quantity;
    }

    public Category getCategory() {
        return category;
    }

    public Set<Dietary> getDietary() {
        return dietary;
    }

    public Instant getPickupFrom() {
        return pickupFrom;
    }

    public Instant getPickupUntil() {
        return pickupUntil;
    }

    public Instant getBestBefore() {
        return bestBefore;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public String getPickupArea() {
        return pickupArea;
    }

    public double getPickupLat() {
        return pickupLat;
    }

    public double getPickupLng() {
        return pickupLng;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public boolean isHasPhoto() {
        return hasPhoto;
    }

    public void setHasPhoto(boolean hasPhoto) {
        this.hasPhoto = hasPhoto;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
