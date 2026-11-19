package com.foodloop.admin;

import java.time.Instant;

import com.foodloop.donor.VerificationStatus;

/** One donor card on the Verify donors screen. A top-level record so JPQL can build it with "select new". */
public record DonorCard(
        Long userId,
        String businessName,
        String contactName,
        String email,
        String pickupAddress,
        String fssaiNo,
        VerificationStatus status,
        String rejectionReason,
        Instant createdAt) {
}
