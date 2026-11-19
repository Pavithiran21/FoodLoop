package com.foodloop.user;

import com.foodloop.common.validation.StrongPassword;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies of /api/me. The email and the role can't be changed (UI-UX.md, Profile). */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    /**
     * A receiver's name and home location (label, lat and lng are set together, or all three are empty to clear it).
     * A donor changes their business name, contact name and pick-up address in PATCH /api/donor/profile instead.
     */
    public record UpdateProfileRequest(
            @NotBlank(message = "Enter your name.") @Size(max = 80, message = "Use at most 80 characters.") String name,
            @Size(max = 250, message = "Use at most 250 characters.") String homeLabel,
            @DecimalMin("-90") @DecimalMax("90") Double homeLat,
            @DecimalMin("-180") @DecimalMax("180") Double homeLng) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Enter your current password.") @Size(max = 200, message = "That password isn't right.") String currentPassword,
            @NotNull(message = "Use at least 8 characters, with a number.") @StrongPassword String newPassword) {
    }

    public record DeleteAccountRequest(
            @NotBlank(message = "Enter your password.") @Size(max = 200, message = "That password isn't right.") String password) {
    }

    public record PasswordChangedResponse(String accessToken) {
    }
}
