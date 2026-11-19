package com.foodloop.auth;

import com.foodloop.common.validation.StrongPassword;
import com.foodloop.common.validation.ValidTimeZone;
import com.foodloop.user.UserResponse;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response bodies of /api/auth. Messages match UI-UX.md. */
public final class AuthDtos {

    public static final String EMAIL_MESSAGE = "Enter a valid email, like name@example.com.";
    public static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private AuthDtos() {
    }

    /** Admin is never an option: the enum has no such value, so a request with it is a 400. */
    public enum SignUpRole {
        DONOR,
        RECEIVER
    }

    /**
     * Sign-up for both roles. The fields only one role needs are checked by RegistrationService (a Donor needs the
     * business and pick-up fields; a Receiver may add a home area), because Bean Validation can't depend on the role.
     */
    public record RegisterRequest(
            @NotNull(message = "Pick Donor or Receiver.") SignUpRole role,
            @NotBlank(message = "Enter your name.") @Size(max = 80, message = "Use at most 80 characters.") String name,
            @NotBlank(message = EMAIL_MESSAGE) @Size(max = 254, message = EMAIL_MESSAGE)
            @Email(regexp = EMAIL_PATTERN, message = EMAIL_MESSAGE) String email,
            @NotNull(message = "Use at least 8 characters, with a number.") @StrongPassword String password,
            // Donor
            @Size(max = 100, message = "Use at most 100 characters.") String businessName,
            @Pattern(regexp = "^(\\d{14})?$", message = "Enter the 14-digit FSSAI number, or leave it empty.") String fssaiNo,
            @Size(max = 250, message = "Use at most 250 characters.") String pickupAddress,
            @Size(max = 80, message = "Use at most 80 characters.") String pickupArea,
            @DecimalMin("-90") @DecimalMax("90") Double pickupLat,
            @DecimalMin("-180") @DecimalMax("180") Double pickupLng,
            @ValidTimeZone String timeZone,
            // Receiver (optional)
            @Size(max = 250, message = "Use at most 250 characters.") String homeLabel,
            @DecimalMin("-90") @DecimalMax("90") Double homeLat,
            @DecimalMin("-180") @DecimalMax("180") Double homeLng) {
    }

    public record LoginRequest(
            @NotBlank(message = EMAIL_MESSAGE) @Size(max = 254, message = EMAIL_MESSAGE) String email,
            @NotBlank(message = "Enter your password.") @Size(max = 200, message = "That password is too long.") String password) {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = EMAIL_MESSAGE) @Size(max = 254, message = EMAIL_MESSAGE)
            @Email(regexp = EMAIL_PATTERN, message = EMAIL_MESSAGE) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "This link is not valid.") String token,
            @NotNull(message = "Use at least 8 characters, with a number.") @StrongPassword String password) {
    }

    public record AuthResponse(UserResponse user, String accessToken) {
    }

    public record MessageResponse(String message) {
    }
}
