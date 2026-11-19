package com.foodloop.report;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.foodloop.report.ReportEnums.Kind;
import com.foodloop.report.ReportEnums.Reason;
import com.foodloop.report.ReportEnums.Status;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A complaint about a donor (from a receiver, about a listing) or about a receiver (from a donor, about a claim).
 * Neither side sees who reported; Admins do. {@code targetRef} is the listing id or the claim id, so one UNIQUE
 * index (reporter, kind, target) enforces "once per listing" and "once per claim".
 */
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Kind kind;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "claim_id")
    private Long claimId;

    @Column(name = "target_ref", nullable = false)
    private Long targetRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Reason reason;

    @Column(length = 500)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Report() {
    }

    private Report(Kind kind, Long reporterId, Long subjectId, Long listingId, Long claimId, Long targetRef, Reason reason,
            String details) {
        this.kind = kind;
        this.reporterId = reporterId;
        this.subjectId = subjectId;
        this.listingId = listingId;
        this.claimId = claimId;
        this.targetRef = targetRef;
        this.reason = reason;
        this.details = details;
        this.status = Status.OPEN;
    }

    /** A receiver's report about a listing. */
    public static Report aboutListing(Long receiverId, Long donorId, Long listingId, Reason reason, String details) {
        return new Report(Kind.LISTING, receiverId, donorId, listingId, null, listingId, reason, details);
    }

    /** A donor's report about the receiver of a claim. */
    public static Report aboutReceiver(Long donorId, Long receiverId, Long listingId, Long claimId, Reason reason,
            String details) {
        return new Report(Kind.RECEIVER, donorId, receiverId, listingId, claimId, claimId, reason, details);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public void dismiss(Long adminId, Instant now) {
        status = Status.DISMISSED;
        resolvedBy = adminId;
        resolvedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public Long getListingId() {
        return listingId;
    }

    public Long getClaimId() {
        return claimId;
    }

    public Reason getReason() {
        return reason;
    }

    public String getDetails() {
        return details;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
