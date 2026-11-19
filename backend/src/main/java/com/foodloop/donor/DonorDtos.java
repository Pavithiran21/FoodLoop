package com.foodloop.donor;

import com.foodloop.common.validation.ValidTimeZone;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies of /api/donor/profile. Messages match UI-UX.md. */
public final class DonorDtos {

    public static final String DEFAULT_TIME_ZONE = "Asia/Kolkata";
    public static final String FSSAI_MESSAGE = "Enter the 14-digit FSSAI number, or leave it empty.";

    private DonorDtos() {
    }

    public record UpdateDonorProfileRequest(
            @NotBlank(message = "Enter the business name.") @Size(max = 100, message = "Use at most 100 characters.") String businessName,
            @NotBlank(message = "Enter your name.") @Size(max = 80, message = "Use at most 80 characters.") String contactName,
            @Pattern(regexp = "^(\\d{14})?$", message = FSSAI_MESSAGE) String fssaiNo,
            @NotBlank(message = "Find the pick-up address.") @Size(max = 250, message = "Use at most 250 characters.") String pickupAddress,
            @NotBlank(message = "Find the pick-up address.") @Size(max = 80, message = "Use at most 80 characters.") String pickupArea,
            @NotNull(message = "Find the pick-up address.") @DecimalMin("-90") @DecimalMax("90") Double pickupLat,
            @NotNull(message = "Find the pick-up address.") @DecimalMin("-180") @DecimalMax("180") Double pickupLng,
            @ValidTimeZone String timeZone) {
    }
}
