package com.foodloop.admin;

import java.time.Instant;

import com.foodloop.user.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the Admin endpoints. Messages match UI-UX.md. */
public final class AdminDtos {

    public static final String REASON_MESSAGE = "Add a short reason; they'll see it.";

    private AdminDtos() {
    }

    public record DonorCounts(long waiting, long verified, long notVerified) {
    }

    /** The reason is required and is shown to the donor (on their banner and in the email). */
    public record ReasonRequest(
            @NotBlank(message = REASON_MESSAGE) @Size(max = 200, message = "Use at most 200 characters.") String reason) {
    }

    /** What an account looks like in the Accounts list: one word that combines login status and donor verification. */
    public enum AccountStatus {
        ACTIVE,
        WAITING,
        NOT_VERIFIED,
        SUSPENDED
    }

    public record AccountRow(
            Long id,
            String name,
            String email,
            Role role,
            String businessName,
            AccountStatus status,
            String suspendedReason,
            long openReports,
            boolean demo,
            /** False for Admins ("Seed script only") and demo accounts. */
            boolean canSuspend,
            Instant createdAt) {
    }
}
