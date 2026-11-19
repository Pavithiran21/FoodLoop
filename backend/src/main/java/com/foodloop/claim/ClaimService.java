package com.foodloop.claim;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodloop.claim.ClaimDtos.CancelledBy;
import com.foodloop.claim.ClaimDtos.ClaimResponse;
import com.foodloop.claim.ClaimDtos.ClaimRow;
import com.foodloop.claim.ClaimDtos.ListingSummary;
import com.foodloop.claim.ClaimDtos.Pickup;
import com.foodloop.common.DeadlockRetry;
import com.foodloop.common.error.ApiException;
import com.foodloop.common.web.PageQuery;
import com.foodloop.common.web.PageResponse;
import com.foodloop.donor.DonorProfile;
import com.foodloop.donor.DonorProfileRepository;
import com.foodloop.listing.Listing;
import com.foodloop.listing.ListingRepository;
import com.foodloop.listing.ListingStatus;
import com.foodloop.mail.MailEvents.ClaimCancelled;
import com.foodloop.mail.MailEvents.ClaimCompleted;
import com.foodloop.mail.MailEvents.ClaimCreated;
import com.foodloop.mail.MailEvents.ClaimPickedUp;
import com.foodloop.mail.MailEvents.ClaimReady;
import com.foodloop.report.ReportEnums.Kind;
import com.foodloop.report.ReportRepository;
import com.foodloop.user.CurrentUser;
import com.foodloop.user.Role;
import com.foodloop.user.User;
import com.foodloop.user.UserRepository;

/**
 * The signature feature: claiming a listing and moving the claim through its lifecycle (see BACKEND.md).
 *
 * Two rules keep it correct under concurrency:
 *  1. Claiming is one conditional UPDATE on the listing ({@link ListingRepository#claimIfAvailable}); only the request
 *     that gets "1 row changed" wins and inserts the claim. The others get 409. The unique index on
 *     claims.active_listing_id is a second line of defence in the database.
 *  2. Every later move first locks the CLAIM row (SELECT ... FOR UPDATE), checks who may move it from which state
 *     ({@link ClaimMove}), then updates the claim and its LISTING in the same transaction. Always claim first, then
 *     listing, so two moves can never wait for each other. The expiry job uses the same order.
 * Claiming and the moves run through {@link DeadlockRetry}: the foreign-key checks on a status change also lock the users
 * row, so a suspension or account deletion running at the same moment can still deadlock with them.
 */
@Service
public class ClaimService {

    private final ClaimRepository claims;
    private final ListingRepository listings;
    private final UserRepository users;
    private final DonorProfileRepository donorProfiles;
    private final ReportRepository reports;
    private final ApplicationEventPublisher events;
    private final DeadlockRetry retry;
    private final Clock clock;

    public ClaimService(ClaimRepository claims, ListingRepository listings, UserRepository users,
            DonorProfileRepository donorProfiles, ReportRepository reports, ApplicationEventPublisher events,
            DeadlockRetry retry, Clock clock) {
        this.claims = claims;
        this.listings = listings;
        this.users = users;
        this.donorProfiles = donorProfiles;
        this.reports = reports;
        this.events = events;
        this.retry = retry;
        this.clock = clock;
    }

    // ---- Claiming ----

    public ClaimResponse claim(CurrentUser actor, Long listingId) {
        actor.requireReceiver();
        return retry.inTransaction(() -> claimInTransaction(actor, listingId));
    }

    private ClaimResponse claimInTransaction(CurrentUser actor, Long listingId) {
        Instant now = clock.instant();
        if (listings.claimIfAvailable(listingId, now, touched()) != 1) {
            throw claimRefused(listingId);
        }
        // The listing row is now ours until this transaction commits, so nobody else can reach this line for it.
        Claim claim = claims.save(new Claim(listingId, actor.id(), now));
        events.publishEvent(new ClaimCreated(claim.getId()));
        return toResponse(claim, actor);
    }

    /** Why the UPDATE changed nothing: 404 unknown, 409 someone else got it, 422 closed or past its window. */
    private ApiException claimRefused(Long listingId) {
        // A locking read, so it sees the latest committed row. A plain read could still show the row as it was when
        // this transaction began, and a database with snapshot reads (TiDB) would then call a lost race "no longer available".
        Listing listing = listings.lockById(listingId).orElseThrow(() -> ApiException.notFound("Listing"));
        ListingStatus status = listing.getStatus();
        if (status == ListingStatus.CLAIMED || status == ListingStatus.READY || status == ListingStatus.PICKED_UP
                || status == ListingStatus.COMPLETED) {
            return new ApiException(HttpStatus.CONFLICT, "CLAIM_LOST", "Someone claimed this a moment ago.");
        }
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "LISTING_NOT_AVAILABLE",
                "This listing is no longer available: its pick-up window has ended or the donor withdrew it.");
    }

    // ---- Moves (each one transaction, see DeadlockRetry) ----

    public ClaimResponse markReady(CurrentUser actor, Long claimId) {
        return retry.inTransaction(() -> move(actor, claimId, ClaimMove.MARK_READY));
    }

    public ClaimResponse confirmHandover(CurrentUser actor, Long claimId) {
        return retry.inTransaction(() -> move(actor, claimId, ClaimMove.CONFIRM_HANDOVER));
    }

    public ClaimResponse confirmReceipt(CurrentUser actor, Long claimId) {
        return retry.inTransaction(() -> move(actor, claimId, ClaimMove.CONFIRM_RECEIPT));
    }

    public ClaimResponse cancel(CurrentUser actor, Long claimId) {
        return retry.inTransaction(() -> move(actor, claimId, ClaimMove.CANCEL));
    }

    private ClaimResponse move(CurrentUser actor, Long claimId, ClaimMove move) {
        Claim claim = claims.lockById(claimId).orElseThrow(() -> ApiException.notFound("Claim"));
        Listing listing = listings.findById(claim.getListingId()).orElseThrow(() -> ApiException.notFound("Claim"));
        requireParty(actor, claim, listing);                    // 404 for anyone who is not the donor or the receiver
        if (!move.mayBeMadeBy(actor.role())) {                  // 403: the other side's move
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You can't do that on this claim.");
        }
        if (!move.isAllowedFrom(claim.getStatus())) {           // 422: right person, wrong moment
            throw illegal(move);
        }
        Instant now = clock.instant();
        // Lock order: claim (above), then listing.
        Listing locked = listings.lockById(listing.getId()).orElseThrow();
        switch (move) {
            case MARK_READY -> {
                claim.markReady(now);
                locked.moveTo(ListingStatus.READY, now);
                events.publishEvent(new ClaimReady(claim.getId()));
            }
            case CONFIRM_HANDOVER -> {
                claim.markPickedUp(now);
                locked.moveTo(ListingStatus.PICKED_UP, now);
                events.publishEvent(new ClaimPickedUp(claim.getId()));
            }
            case CONFIRM_RECEIPT -> {
                claim.markCompleted(now);
                locked.moveTo(ListingStatus.COMPLETED, now);
                events.publishEvent(new ClaimCompleted(claim.getId()));
            }
            case CANCEL -> {
                Long otherSide = actor.isDonor() ? claim.getReceiverId() : locked.getDonorId();
                applyCancel(claim, locked, actor.id(), otherSide, now);
            }
        }
        return toResponse(claim, actor);
    }

    /**
     * Ends a claim as CANCELLED and decides what happens to its listing: back to LISTED while the pick-up window is
     * open, closed as EXPIRED once it has ended. Shared by the donor's and the receiver's cancel, suspensions and
     * account deletion (the caller holds the claim lock, and passes the locked listing).
     */
    public void applyCancel(Claim claim, Listing listing, Long cancelledBy, Long notifyUserId, Instant now) {
        claim.markCancelled(cancelledBy, now);
        if (listing.windowEnded(now)) {
            listing.moveTo(ListingStatus.EXPIRED, now);
        } else {
            listing.moveTo(ListingStatus.LISTED, now);
        }
        events.publishEvent(new ClaimCancelled(claim.getId(), notifyUserId));
    }

    /**
     * Cancels one claim because an account was suspended (acting = the Admin) or deleted (acting = the person). The
     * email goes to the side that is NOT {@code affectedUserId}. Locks the claim then the listing and looks at the
     * claim's current state again, so it is safe next to the people's own moves. A claim that was already handed over
     * (PICKED_UP) or finished is left alone: the food has changed hands and the claim completes by itself.
     */
    @Transactional
    public void cancelForAccountChange(Long claimId, Long actingUserId, Long affectedUserId) {
        Claim claim = claims.lockById(claimId).orElse(null);
        if (claim == null || !ClaimStatus.BEFORE_PICKUP.contains(claim.getStatus())) {
            return;
        }
        Listing listing = listings.lockById(claim.getListingId()).orElseThrow();
        Long notify = affectedUserId.equals(claim.getReceiverId()) ? listing.getDonorId() : claim.getReceiverId();
        applyCancel(claim, listing, actingUserId, notify, clock.instant());
    }

    // ---- Reading ----

    @Transactional(readOnly = true)
    public ClaimResponse get(CurrentUser actor, Long claimId) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> ApiException.notFound("Claim"));
        Listing listing = listings.findById(claim.getListingId()).orElseThrow(() -> ApiException.notFound("Claim"));
        requireParty(actor, claim, listing);
        return toResponse(claim, actor, listing);
    }

    /** My claims: tab ACTIVE (claimed, ready, picked up) or PAST (completed, cancelled, expired). Receivers only. */
    @Transactional(readOnly = true)
    public PageResponse<ClaimRow> myClaims(CurrentUser actor, String tab, Integer page, Integer size) {
        actor.requireReceiver();
        boolean active = !"PAST".equalsIgnoreCase(tab);
        Pageable base = PageQuery.unsorted(page, size);
        Pageable pageable = PageRequest.of(base.getPageNumber(), base.getPageSize(),
                active ? Sort.by("claimedAt").descending().and(Sort.by("id").descending()) : Sort.by("updatedAt").descending().and(Sort.by("id").descending()));
        Page<Claim> found = claims.findByReceiverIdAndStatusIn(actor.id(), active ? ClaimStatus.ACTIVE : ClaimStatus.PAST, pageable);
        return PageResponse.of(found, claim -> {
            Listing listing = listings.findById(claim.getListingId()).orElseThrow();
            return toRow(claim, listing);
        });
    }

    @Transactional(readOnly = true)
    public long activeCount(CurrentUser actor) {
        actor.requireReceiver();
        return claims.countByReceiverIdAndStatusIn(actor.id(), ClaimStatus.ACTIVE);
    }

    // ---- Helpers ----

    /** Only the claim's receiver and its listing's donor may see or move it; everyone else gets 404 (no IDOR leak). */
    private static void requireParty(CurrentUser actor, Claim claim, Listing listing) {
        boolean receiver = actor.isReceiver() && claim.getReceiverId().equals(actor.id());
        boolean donor = actor.isDonor() && listing.getDonorId().equals(actor.id());
        if (!receiver && !donor) {
            throw ApiException.notFound("Claim");
        }
    }

    private static ApiException illegal(ClaimMove move) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "ILLEGAL_TRANSITION",
                move.illegalMessage() + " A claim goes Claimed → Ready → Picked up → Completed, one step at a time.");
    }

    private static Instant touched() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private ClaimResponse toResponse(Claim claim, CurrentUser viewer) {
        return toResponse(claim, viewer, listings.findById(claim.getListingId()).orElseThrow());
    }

    private ClaimResponse toResponse(Claim claim, CurrentUser viewer, Listing listing) {
        DonorProfile donor = donorProfiles.findById(listing.getDonorId()).orElseThrow();
        User receiver = users.findById(claim.getReceiverId()).orElseThrow();
        Pickup pickup = new Pickup(listing.getPickupAddress(), listing.getPickupArea(), listing.getPickupLat(), listing.getPickupLng());
        CancelledBy cancelledBy = null;
        if (claim.getCancelledBy() != null) {
            Role who = users.findById(claim.getCancelledBy()).map(User::getRole).orElse(Role.ADMIN);
            cancelledBy = new CancelledBy(who, claim.getCancelledBy().equals(viewer.id()));
        }
        // The donor reports the claim's receiver (once per claim); the receiver reports the listing (once per listing).
        boolean reported = viewer.isDonor()
                ? reports.existsByReporterIdAndKindAndTargetRef(viewer.id(), Kind.RECEIVER, claim.getId())
                : reports.existsByReporterIdAndKindAndTargetRef(viewer.id(), Kind.LISTING, listing.getId());
        ListingSummary summary = new ListingSummary(listing.getId(), listing.getTitle(), listing.getQuantity(), listing.getCategory(),
                Set.copyOf(listing.getDietary()), listing.getPickupFrom(), listing.getPickupUntil(), listing.getBestBefore(),
                listing.getTimeZone(), listing.isHasPhoto(), listing.getStatus(), listing.getCreatedAt());
        return new ClaimResponse(claim.getId(), claim.number(), claim.getStatus(), viewer.role(), summary,
                donor.getBusinessName(), receiver.getName(), pickup, claim.getClaimedAt(), claim.getReadyAt(), claim.getPickedUpAt(),
                claim.getCompletedAt(), claim.isAutoCompleted(), claim.getCancelledAt(), cancelledBy, claim.getExpiredAt(),
                ClaimMove.available(claim.getStatus(), viewer.role()), reported);
    }

    private ClaimRow toRow(Claim claim, Listing listing) {
        String donorName = donorProfiles.findById(listing.getDonorId()).map(DonorProfile::getBusinessName).orElse(User.FORMER_DONOR);
        return new ClaimRow(claim.getId(), claim.number(), claim.getStatus(), listing.getId(), listing.getTitle(),
                listing.getQuantity(), listing.getCategory(), listing.isHasPhoto(), donorName, listing.getPickupFrom(),
                listing.getPickupUntil(), listing.getTimeZone(), claim.getUpdatedAt());
    }

    /** Used by the listing and report services to look up the claims on one listing, oldest first. */
    public List<Claim> claimsOf(Long listingId) {
        return claims.findByListingIdOrderById(listingId);
    }
}
