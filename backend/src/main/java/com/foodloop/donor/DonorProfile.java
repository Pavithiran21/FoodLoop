package com.foodloop.donor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** The business behind a Donor account: its pick-up place, FSSAI number and the Admin's decision. */
@Entity
@Table(name = "donor_profiles")
public class DonorProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "business_name", nullable = false, length = 100)
    private String businessName;

    @Column(name = "fssai_no", length = 14)
    private String fssaiNo;

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
    @Column(name = "verification_status", nullable = false, length = 12)
    private VerificationStatus verificationStatus;

    @Column(name = "rejection_reason", length = 200)
    private String rejectionReason;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "review_requested_at")
    private Instant reviewRequestedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DonorProfile() {
    }

    public DonorProfile(Long userId, String businessName, String fssaiNo, String pickupAddress, String pickupArea,
            double pickupLat, double pickupLng, String timeZone) {
        this.userId = userId;
        this.verificationStatus = VerificationStatus.WAITING;
        update(businessName, fssaiNo, pickupAddress, pickupArea, pickupLat, pickupLng, timeZone);
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

    public void update(String businessName, String fssaiNo, String pickupAddress, String pickupArea, double pickupLat,
            double pickupLng, String timeZone) {
        this.businessName = businessName;
        this.fssaiNo = fssaiNo;
        this.pickupAddress = pickupAddress;
        this.pickupArea = pickupArea;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.timeZone = timeZone;
    }

    public boolean isVerified() {
        return verificationStatus == VerificationStatus.VERIFIED;
    }

    public void verify(Long adminId, Instant now) {
        this.verificationStatus = VerificationStatus.VERIFIED;
        this.rejectionReason = null;
        this.verifiedAt = now;
        this.verifiedBy = adminId;
    }

    public void reject(Long adminId, String reason) {
        this.verificationStatus = VerificationStatus.NOT_VERIFIED;
        this.rejectionReason = reason;
        this.verifiedAt = null;
        this.verifiedBy = adminId;
    }

    /** "Ask for review again": back to the Admins' waiting list; the old reason is kept until they decide. */
    public void requestReview(Instant now) {
        this.verificationStatus = VerificationStatus.WAITING;
        this.reviewRequestedAt = now;
    }

    /** Removes what identifies the business when the donor deletes their account. */
    public void anonymise(String formerName) {
        this.businessName = formerName;
        this.fssaiNo = null;
        this.pickupAddress = "Removed";
        this.pickupArea = "Removed";
        this.pickupLat = 0;
        this.pickupLng = 0;
    }

    public Long getUserId() {
        return userId;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getFssaiNo() {
        return fssaiNo;
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

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
