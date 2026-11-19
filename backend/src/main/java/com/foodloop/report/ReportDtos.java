package com.foodloop.report;

import java.time.Instant;
import java.util.List;

import com.foodloop.report.ReportEnums.Kind;
import com.foodloop.report.ReportEnums.Reason;
import com.foodloop.report.ReportEnums.Status;
import com.foodloop.user.Role;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies for reports. */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record ReportRequest(
            @NotNull(message = "Pick a reason.") Reason reason,
            @Size(max = 500, message = "Use at most 500 characters.") String details) {
    }

    /** Someone named in a report: the account, and its role (a donor or a receiver). */
    public record Party(Long id, String name, Role role) {
    }

    public record ListingRef(Long id, String title) {
    }

    public record ClaimRef(Long id, String number) {
    }

    /** One row of the Admin queue. Admins see who reported; the two sides of a report never do. */
    public record ReportRow(Long id, Kind kind, Status status, Reason reason, Instant createdAt, Party about, Party from,
            ListingRef listing, ClaimRef claim) {
    }

    /** An earlier report about the same account, shown on the report view. */
    public record PastReport(Long id, Kind kind, Status status, Reason reason, Instant createdAt) {
    }

    public record ReportView(ReportRow report, String details, Instant resolvedAt, List<PastReport> pastReports,
            /** True while the account in {@code about} can still be suspended from this report. */
            boolean canSuspend) {
    }
}
