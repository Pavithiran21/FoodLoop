package com.foodloop.admin;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodloop.claim.Claim;
import com.foodloop.claim.ClaimRepository;
import com.foodloop.common.DeadlockRetry;
import com.foodloop.common.error.ApiException;
import com.foodloop.common.web.PageQuery;
import com.foodloop.common.web.PageResponse;
import com.foodloop.listing.Listing;
import com.foodloop.listing.ListingRepository;
import com.foodloop.report.Report;
import com.foodloop.report.ReportDtos.ClaimRef;
import com.foodloop.report.ReportDtos.ListingRef;
import com.foodloop.report.ReportDtos.Party;
import com.foodloop.report.ReportDtos.PastReport;
import com.foodloop.report.ReportDtos.ReportRow;
import com.foodloop.report.ReportDtos.ReportView;
import com.foodloop.report.ReportEnums.Status;
import com.foodloop.report.ReportRepository;
import com.foodloop.donor.DonorProfileRepository;
import com.foodloop.user.CurrentUser;
import com.foodloop.user.Role;
import com.foodloop.user.User;
import com.foodloop.user.UserRepository;

/**
 * The Admin's Reports queue: open reports first (oldest first), then the settled ones. An Admin opens a report, then
 * either dismisses it (nothing wrong) or suspends the account it is about (every open report about that account is then
 * ACTIONED). Neither side of a report ever sees who reported; only Admins do.
 */
@Service
public class AdminReportService {

    private final ReportRepository reports;
    private final UserRepository users;
    private final DonorProfileRepository donorProfiles;
    private final ListingRepository listings;
    private final ClaimRepository claims;
    private final AccountService accountService;
    private final DeadlockRetry retry;
    private final Clock clock;

    public AdminReportService(ReportRepository reports, UserRepository users, DonorProfileRepository donorProfiles,
            ListingRepository listings, ClaimRepository claims, AccountService accountService, DeadlockRetry retry,
            Clock clock) {
        this.reports = reports;
        this.users = users;
        this.donorProfiles = donorProfiles;
        this.listings = listings;
        this.claims = claims;
        this.accountService = accountService;
        this.retry = retry;
        this.clock = clock;
    }

    /** status = null lists everything (open first); otherwise only that status. */
    @Transactional(readOnly = true)
    public PageResponse<ReportRow> list(CurrentUser actor, Status status, Integer page, Integer size) {
        actor.requireAdmin();
        Pageable pageable = PageQuery.unsorted(page, size);
        Page<Report> found = status == null ? reports.findQueue(pageable) : reports.findByStatusOrderByCreatedAtAscIdAsc(status, pageable);
        return new PageResponse<>(rows(found.getContent()), found.getNumber() + 1, found.getSize(), found.getTotalElements(),
                found.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ReportView view(CurrentUser actor, Long reportId) {
        actor.requireAdmin();
        Report report = reports.findById(reportId).orElseThrow(() -> ApiException.notFound("Report"));
        ReportRow row = rows(List.of(report)).get(0);
        List<PastReport> past = reports.findTop10BySubjectIdAndIdNotOrderByCreatedAtDesc(report.getSubjectId(), reportId).stream()
                .map(r -> new PastReport(r.getId(), r.getKind(), r.getStatus(), r.getReason(), r.getCreatedAt())).toList();
        boolean canSuspend = report.getStatus() == Status.OPEN && users.findById(report.getSubjectId())
                .filter(User::isActive).filter(u -> u.getRole() != Role.ADMIN && !u.isDemo()).isPresent();
        return new ReportView(row, report.getDetails(), report.getResolvedAt(), past, canSuspend);
    }

    @Transactional
    public void dismiss(CurrentUser actor, Long reportId) {
        actor.requireAdmin();
        Report report = reports.lockById(reportId).orElseThrow(() -> ApiException.notFound("Report"));
        if (report.getStatus() != Status.OPEN) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "ILLEGAL_STATE", "This report is already settled.");
        }
        report.dismiss(actor.id(), clock.instant());
    }

    /**
     * Suspends the account the report is about; AccountService also marks every open report about it ACTIONED. One
     * transaction for both, run again by {@link DeadlockRetry} if it loses a deadlock (the suspension joins it).
     */
    public void suspend(CurrentUser actor, Long reportId, String reason) {
        actor.requireAdmin();
        retry.inTransaction(() -> {
            Report report = reports.lockById(reportId).orElseThrow(() -> ApiException.notFound("Report"));
            if (report.getStatus() != Status.OPEN) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "ILLEGAL_STATE", "This report is already settled.");
            }
            accountService.suspend(actor, report.getSubjectId(), reason);
        });
    }

    /** Names and titles for a page of reports, looked up with a few queries instead of one per row. */
    private List<ReportRow> rows(List<Report> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = page.stream().flatMap(r -> Stream.of(r.getSubjectId(), r.getReporterId())).collect(Collectors.toSet());
        Map<Long, User> people = users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Listing> listingById = listings.findAllById(page.stream().map(Report::getListingId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Listing::getId, l -> l));
        Map<Long, Claim> claimById = claims.findAllById(page.stream().map(Report::getClaimId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Claim::getId, c -> c));
        return page.stream().map(r -> {
            Listing listing = listingById.get(r.getListingId());
            Claim claim = r.getClaimId() == null ? null : claimById.get(r.getClaimId());
            return new ReportRow(r.getId(), r.getKind(), r.getStatus(), r.getReason(), r.getCreatedAt(),
                    party(people.get(r.getSubjectId())), party(people.get(r.getReporterId())),
                    listing == null ? null : new ListingRef(listing.getId(), listing.getTitle()),
                    claim == null ? null : new ClaimRef(claim.getId(), claim.number()));
        }).toList();
    }

    /** A donor is named by their business; everyone else by their name. */
    private Party party(User user) {
        String name = user.getRole() == Role.DONOR
                ? donorProfiles.findById(user.getId()).map(p -> p.getBusinessName()).orElse(user.getName())
                : user.getName();
        return new Party(user.getId(), name, user.getRole());
    }
}
