package com.foodloop.report;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodloop.claim.Claim;
import com.foodloop.claim.ClaimRepository;
import com.foodloop.common.error.ApiException;
import com.foodloop.common.error.DatabaseErrors;
import com.foodloop.common.error.ErrorDetail;
import com.foodloop.common.web.RateLimiter;
import com.foodloop.common.web.RateLimiter.Limit;
import com.foodloop.listing.Listing;
import com.foodloop.listing.ListingRepository;
import com.foodloop.report.ReportDtos.ReportRequest;
import com.foodloop.report.ReportEnums.Kind;
import com.foodloop.user.CurrentUser;

/**
 * Creating reports. A receiver reports a LISTING (so its donor) once per listing; a donor reports the RECEIVER of one of
 * their claims once per claim. The UNIQUE index (reporter, kind, target) is what really enforces "once": the existence
 * check only gives the friendly message, and a duplicate that slips past it (two clicks at once) is caught below.
 * The text a person writes is never logged.
 */
@Service
public class ReportService {

    private final ReportRepository reports;
    private final ListingRepository listings;
    private final ClaimRepository claims;
    private final RateLimiter rateLimiter;

    public ReportService(ReportRepository reports, ListingRepository listings, ClaimRepository claims, RateLimiter rateLimiter) {
        this.reports = reports;
        this.listings = listings;
        this.claims = claims;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public void reportListing(CurrentUser actor, Long listingId, ReportRequest request) {
        actor.requireReceiver();
        checkReason(request, Kind.LISTING);
        Listing listing = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing"));
        rateLimiter.consume(Limit.REPORT_USER, String.valueOf(actor.id()));
        if (reports.existsByReporterIdAndKindAndTargetRef(actor.id(), Kind.LISTING, listingId)) {
            throw alreadyReported("listing");
        }
        save(Report.aboutListing(actor.id(), listing.getDonorId(), listingId, request.reason(), clean(request.details())), "listing");
    }

    @Transactional
    public void reportReceiver(CurrentUser actor, Long claimId, ReportRequest request) {
        actor.requireDonor();
        checkReason(request, Kind.RECEIVER);
        Claim claim = claims.findById(claimId).orElseThrow(() -> ApiException.notFound("Claim"));
        Listing listing = listings.findById(claim.getListingId()).orElseThrow(() -> ApiException.notFound("Claim"));
        if (!listing.getDonorId().equals(actor.id())) {
            throw ApiException.notFound("Claim");   // someone else's claim: the same answer as a claim that doesn't exist
        }
        rateLimiter.consume(Limit.REPORT_USER, String.valueOf(actor.id()));
        if (reports.existsByReporterIdAndKindAndTargetRef(actor.id(), Kind.RECEIVER, claimId)) {
            throw alreadyReported("claim");
        }
        save(Report.aboutReceiver(actor.id(), claim.getReceiverId(), listing.getId(), claimId, request.reason(),
                clean(request.details())), "claim");
    }

    private void save(Report report, String what) {
        try {
            reports.saveAndFlush(report);
        } catch (DataIntegrityViolationException ex) {
            if (DatabaseErrors.isDuplicateKey(ex)) {
                throw alreadyReported(what);
            }
            throw ex;
        }
    }

    private static void checkReason(ReportRequest request, Kind kind) {
        if (!request.reason().isFor(kind)) {
            throw ApiException.invalidField("reason", "Pick one of the reasons in the list.");
        }
    }

    private static String clean(String details) {
        return details == null || details.isBlank() ? null : details.trim();
    }

    private static ApiException alreadyReported(String what) {
        String message = "You've already reported this " + what + ".";
        return new ApiException(HttpStatus.CONFLICT, "ALREADY_REPORTED", message, List.of(new ErrorDetail("reason", message)));
    }
}
