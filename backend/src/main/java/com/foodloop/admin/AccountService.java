package com.foodloop.admin;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodloop.admin.AdminDtos.AccountRow;
import com.foodloop.admin.AdminDtos.AccountStatus;
import com.foodloop.auth.RefreshTokenRepository;
import com.foodloop.claim.Claim;
import com.foodloop.claim.ClaimRepository;
import com.foodloop.claim.ClaimService;
import com.foodloop.claim.ClaimStatus;
import com.foodloop.common.DeadlockRetry;
import com.foodloop.common.error.ApiException;
import com.foodloop.common.web.PageQuery;
import com.foodloop.common.web.PageResponse;
import com.foodloop.listing.ListingService;
import com.foodloop.mail.MailEvents.AccountReinstated;
import com.foodloop.mail.MailEvents.AccountSuspended;
import com.foodloop.report.ReportRepository;
import com.foodloop.user.CurrentUser;
import com.foodloop.user.Role;
import com.foodloop.user.User;
import com.foodloop.user.UserRepository;

import org.springframework.data.domain.Pageable;

/**
 * The Accounts screen: search, suspend and reinstate. Suspending is the strongest thing an Admin can do, so it is guarded:
 * Admins and demo accounts can't be suspended; the user row is locked so two Admins can't suspend one person twice; and
 * everything the account had open is closed in the same transaction (its listings are withdrawn, its claims cancelled with
 * the other side emailed, its sessions ended, and every open report about it is marked ACTIONED).
 */
@Service
public class AccountService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final ClaimRepository claims;
    private final ClaimService claimService;
    private final ListingService listingService;
    private final ReportRepository reports;
    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final DeadlockRetry retry;
    private final Clock clock;

    public AccountService(UserRepository users, RefreshTokenRepository refreshTokens, ClaimRepository claims,
            ClaimService claimService, ListingService listingService, ReportRepository reports, NamedParameterJdbcTemplate jdbc,
            ApplicationEventPublisher events, DeadlockRetry retry, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.claims = claims;
        this.claimService = claimService;
        this.listingService = listingService;
        this.reports = reports;
        this.jdbc = jdbc;
        this.events = events;
        this.retry = retry;
        this.clock = clock;
    }

    /** One transaction, run again by {@link DeadlockRetry} if it loses a deadlock against a claim move. */
    public void suspend(CurrentUser actor, Long userId, String reason) {
        actor.requireAdmin();
        retry.inTransaction(() -> suspendInTransaction(actor, userId, reason));
    }

    private void suspendInTransaction(CurrentUser actor, Long userId, String reason) {
        User target = users.lockById(userId).orElseThrow(() -> ApiException.notFound("Account"));
        if (target.getRole() == Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_NOT_SUSPENDABLE", "Admins can't be suspended.");
        }
        if (target.isDemo()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DEMO_ACCOUNT_LOCKED", "Demo accounts can't be suspended.");
        }
        if (!target.isActive()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "ILLEGAL_STATE", "That account is already suspended or deleted.");
        }
        String storedReason = reason.trim();
        target.suspend(storedReason, clock.instant());
        refreshTokens.deleteAllForUser(userId);

        // Close what the account had open. The claims are cancelled one by one, each under its own claim lock. For a
        // donor this comes FIRST: cancelling puts a listing back on the list, and withdrawing afterwards takes it off.
        List<Claim> active = target.getRole() == Role.DONOR
                ? claims.findActiveForDonor(userId, ClaimStatus.BEFORE_PICKUP)
                : claims.findByReceiverIdAndStatusIn(userId, ClaimStatus.BEFORE_PICKUP);
        for (Claim claim : active) {
            claimService.cancelForAccountChange(claim.getId(), actor.id(), userId);
        }
        if (target.getRole() == Role.DONOR) {
            listingService.withdrawOpenListings(userId);
        }
        reports.actionOpenAbout(userId, actor.id(), clock.instant());
        events.publishEvent(new AccountSuspended(userId, storedReason));
    }

    @Transactional
    public void reinstate(CurrentUser actor, Long userId) {
        actor.requireAdmin();
        User target = users.lockById(userId).orElseThrow(() -> ApiException.notFound("Account"));
        if (target.getStatus() != com.foodloop.user.UserStatus.SUSPENDED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "ILLEGAL_STATE", "That account isn't suspended.");
        }
        target.reinstate();
        events.publishEvent(new AccountReinstated(userId));
    }

    /** The Accounts list: search by name, email or business; filter by role and status. Never lists deleted accounts. */
    @Transactional(readOnly = true)
    public PageResponse<AccountRow> list(CurrentUser actor, String q, Role role, AccountStatus status, Integer page, Integer size) {
        actor.requireAdmin();
        Pageable paging = PageQuery.unsorted(page, size);
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE u.status <> 'DELETED'");
        if (q != null && !q.isBlank()) {
            where.append(" AND (LOWER(u.name) LIKE :q OR LOWER(u.email) LIKE :q OR LOWER(COALESCE(d.business_name, '')) LIKE :q)");
            params.addValue("q", "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (role != null) {
            where.append(" AND u.role = :role");
            params.addValue("role", role.name());
        }
        String statusCase = """
                CASE WHEN u.status = 'SUSPENDED' THEN 'SUSPENDED'
                     WHEN u.role = 'DONOR' AND d.verification_status = 'WAITING' THEN 'WAITING'
                     WHEN u.role = 'DONOR' AND d.verification_status = 'NOT_VERIFIED' THEN 'NOT_VERIFIED'
                     ELSE 'ACTIVE' END""";
        if (status != null) {
            where.append(" AND ").append(statusCase.replace("\n", " ")).append(" = :status");
            params.addValue("status", status.name());
        }
        String from = " FROM users u LEFT JOIN donor_profiles d ON d.user_id = u.id" + where;

        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, params, Long.class);
        if (total == null || total == 0) {
            return new PageResponse<>(List.of(), paging.getPageNumber() + 1, paging.getPageSize(), 0, 0);
        }
        params.addValue("limit", paging.getPageSize()).addValue("offset", paging.getOffset());
        List<AccountRow> rows = jdbc.query("SELECT u.id, u.name, u.email, u.role, d.business_name, " + statusCase + " AS account_status, "
                + "u.suspended_reason, u.is_demo, u.created_at, "
                + "(SELECT COUNT(*) FROM reports r WHERE r.subject_id = u.id AND r.status = 'OPEN') AS open_reports"
                + from + " ORDER BY u.name, u.id LIMIT :limit OFFSET :offset", params, (rs, i) -> {
                    Role rowRole = Role.valueOf(rs.getString("role"));
                    AccountStatus rowStatus = AccountStatus.valueOf(rs.getString("account_status"));
                    boolean demo = rs.getBoolean("is_demo");
                    boolean canSuspend = rowRole != Role.ADMIN && !demo && rowStatus != AccountStatus.SUSPENDED;
                    return new AccountRow(rs.getLong("id"), rs.getString("name"), rs.getString("email"), rowRole,
                            rs.getString("business_name"), rowStatus, rs.getString("suspended_reason"), rs.getLong("open_reports"),
                            demo, canSuspend, rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
                });
        int totalPages = (int) Math.ceil(total / (double) paging.getPageSize());
        return new PageResponse<>(rows, paging.getPageNumber() + 1, paging.getPageSize(), total, totalPages);
    }
}
