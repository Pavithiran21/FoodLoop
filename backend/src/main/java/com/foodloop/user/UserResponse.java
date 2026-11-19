package com.foodloop.user;

import com.foodloop.donor.VerificationStatus;

/**
 * The profile the frontend keeps after login. {@code home} is a receiver's saved search centre; {@code donor} is set
 * for Donors only and carries the verification status the board's banner needs.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        boolean demo,
        Home home,
        DonorInfo donor) {

    public record Home(String label, double lat, double lng) {
    }

    public record DonorInfo(
            String businessName,
            String fssaiNo,
            String pickupAddress,
            String pickupArea,
            double pickupLat,
            double pickupLng,
            String timeZone,
            VerificationStatus verificationStatus,
            String rejectionReason) {
    }
}
