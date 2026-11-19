package com.foodloop.listing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodloop.claim.Claim;
import com.foodloop.claim.ClaimMove;
import com.foodloop.claim.ClaimRepository;
import com.foodloop.common.DeadlockRetry;
import com.foodloop.common.error.ApiException;
import com.foodloop.common.error.ErrorDetail;
import com.foodloop.common.web.PageQuery;
import com.foodloop.common.web.PageResponse;
import com.foodloop.donor.DonorProfile;
import com.foodloop.donor.DonorService;
import com.foodloop.listing.ListingDtos.ClaimBrief;
import com.foodloop.listing.ListingDtos.DonorListing;
import com.foodloop.listing.ListingDtos.ListingRequest;
import com.foodloop.user.CurrentUser;
import com.foodloop.user.Role;
import com.foodloop.user.User;
import com.foodloop.user.UserRepository;

/**
 * A donor's own listings: create, view, edit, withdraw, the board and History. Every method starts with the role
 * check and loads the listing through the donor's id, so a donor can never reach another donor's listing (404).
 * Editing and withdrawing lock the listing row first and need it to be LISTED, so they take turns with a claim.
 */
@Service
public class ListingService {

    /** The board's five columns; DONE_TODAY is completed, expired and withdrawn listings closed today. */
    public enum BoardState {
        LISTED(Set.of(ListingStatus.LISTED)),
        CLAIMED(Set.of(ListingStatus.CLAIMED)),
        READY(Set.of(ListingStatus.READY)),
        PICKED_UP(Set.of(ListingStatus.PICKED_UP)),
        DONE_TODAY(ListingStatus.CLOSED);

        final Set<ListingStatus> statuses;

        BoardState(Set<ListingStatus> statuses) {
            this.statuses = statuses;
        }
    }

    /** History filter: how a listing ended. */
    public enum Outcome {
        COMPLETED,
        EXPIRED,
        WITHDRAWN
    }

    private static final Duration MAX_WINDOW_AHEAD = Duration.ofDays(7);

    private final ListingRepository listings;
    private final ClaimRepository claims;
    private final UserRepository users;
    private final DonorService donors;
    private final DeadlockRetry retry;
    private final Clock clock;

    public ListingService(ListingRepository listings, ClaimRepository claims, UserRepository users, DonorService donors,
            DeadlockRetry retry, Clock clock) {
        this.listings = listings;
        this.claims = claims;
        this.users = users;
        this.donors = donors;
        this.retry = retry;
        this.clock = clock;
    }

    @Transactional
    public DonorListing create(CurrentUser actor, ListingRequest request) {
        actor.requireDonor();
        DonorProfile profile = donors.requireVerified(actor.id());   // 403 DONOR_NOT_VERIFIED
        validate(request, clock.instant());
        Listing listing = listings.save(new Listing(actor.id(), request.title().trim(), request.quantity().trim(),
                request.category(), request.dietary(), request.pickupFrom(), request.pickupUntil(), request.bestBefore(),
                profile.getPickupAddress(), profile.getPickupArea(), profile.getPickupLat(), profile.getPickupLng(),
                profile.getTimeZone()));
        return toDonorListing(listing, null);
    }

    @Transactional(readOnly = true)
    public DonorListing get(CurrentUser actor, Long id) {
        actor.requireDonor();
        Listing listing = own(actor, id);
        return map(List.of(listing)).get(0);
    }

    @Transactional
    public DonorListing update(CurrentUser actor, Long id, ListingRequest request) {
        actor.requireDonor();
        donors.requireVerified(actor.id());
        Listing listing = lockOwnUnclaimed(actor, id);
        validate(request, clock.instant());
        listing.edit(request.title().trim(), request.quantity().trim(), request.category(), request.dietary(),
                request.pickupFrom(), request.pickupUntil(), request.bestBefore());
        return toDonorListing(listing, null);
    }

    /** A status change, so it runs through {@link DeadlockRetry} like the claim moves. */
    public DonorListing withdraw(CurrentUser actor, Long id) {
        actor.requireDonor();
        return retry.inTransaction(() -> {
            Listing listing = lockOwnUnclaimed(actor, id);
            listing.moveTo(ListingStatus.WITHDRAWN, clock.instant());
            return toDonorListing(listing, null);
        });
    }

    /** One column of the board (or all closed listings of today), newest activity first. */
    @Transactional(readOnly = true)
    public PageResponse<DonorListing> board(CurrentUser actor, BoardState state, Integer page, Integer size) {
        actor.requireDonor();
        Pageable base = PageQuery.unsorted(page, size);
        Sort sort = state == BoardState.LISTED ? Sort.by("pickupUntil").and(Sort.by("id"))
                : Sort.by("updatedAt").descending().and(Sort.by("id").descending());
        Pageable pageable = PageRequest.of(base.getPageNumber(), base.getPageSize(), sort);
        Page<Listing> found;
        if (state == BoardState.DONE_TODAY) {
            found = listings.findByDonorIdAndStatusInAndClosedAtGreaterThanEqual(actor.id(), state.statuses,
                    startOfTodayFor(actor), pageable);
        } else {
            found = listings.findByDonorIdAndStatusIn(actor.id(), state.statuses, pageable);
        }
        return pageOf(found);
    }

    /** History: every finished listing, optionally one outcome, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<DonorListing> history(CurrentUser actor, Outcome outcome, Integer page, Integer size) {
        actor.requireDonor();
        Pageable base = PageQuery.unsorted(page, size);
        Pageable pageable = PageRequest.of(base.getPageNumber(), base.getPageSize(),
                Sort.by("closedAt").descending().and(Sort.by("id").descending()));
        Set<ListingStatus> statuses = outcome == null ? ListingStatus.CLOSED : Set.of(ListingStatus.valueOf(outcome.name()));
        return pageOf(listings.findByDonorIdAndStatusIn(actor.id(), statuses, pageable));
    }

    // ---- For the account services (suspension, deletion) ----

    /** Withdraws every LISTED listing of a donor (the claimed ones are cancelled by ClaimService). */
    @Transactional
    public int withdrawOpenListings(Long donorId) {
        Instant now = clock.instant();
        List<Listing> open = listings.findByDonorIdAndStatus(donorId, ListingStatus.LISTED);
        for (Listing listing : open) {
            Listing locked = listings.lockById(listing.getId()).orElseThrow();
            if (locked.getStatus() == ListingStatus.LISTED) {
                locked.moveTo(ListingStatus.WITHDRAWN, now);
            }
        }
        return open.size();
    }

    // ---- Helpers ----

    private Listing own(CurrentUser actor, Long id) {
        return listings.findById(id).filter(l -> l.getDonorId().equals(actor.id())).orElseThrow(() -> ApiException.notFound("Listing"));
    }

    /** Locks the listing and checks it is the donor's and that nobody has claimed it (422 otherwise). */
    Listing lockOwnUnclaimed(CurrentUser actor, Long id) {
        Listing listing = listings.lockById(id).filter(l -> l.getDonorId().equals(actor.id()))
                .orElseThrow(() -> ApiException.notFound("Listing"));
        if (listing.getStatus() != ListingStatus.LISTED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "LISTING_LOCKED",
                    "A listing can be changed only while nobody has claimed it.");
        }
        return listing;
    }

    /** The time rules from UI-UX.md, reported field by field. */
    private static void validate(ListingRequest request, Instant now) {
        List<ErrorDetail> problems = new ArrayList<>();
        if (!request.pickupUntil().isAfter(request.pickupFrom())) {
            problems.add(new ErrorDetail("pickupUntil", "Pick-up must end after it starts."));
        } else if (!request.pickupUntil().isAfter(now)) {
            problems.add(new ErrorDetail("pickupUntil", "Pick-up can't end in the past."));
        } else if (request.pickupUntil().isAfter(now.plus(MAX_WINDOW_AHEAD))) {
            problems.add(new ErrorDetail("pickupUntil", "Pick-up can be at most 7 days from now."));
        }
        if (request.bestBefore().isBefore(request.pickupFrom())) {
            problems.add(new ErrorDetail("bestBefore", "The best-before time can't be before the pick-up starts."));
        }
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields are not valid.", problems);
        }
    }

    /** Midnight today in the donor's time zone, as an instant: "Done today" means closed since then. */
    private Instant startOfTodayFor(CurrentUser actor) {
        ZoneId zone = donors.zoneOf(actor.id());
        return clock.instant().atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
    }

    private PageResponse<DonorListing> pageOf(Page<Listing> found) {
        List<DonorListing> items = map(found.getContent());
        return new PageResponse<>(items, found.getNumber() + 1, found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    /** Adds each listing's latest claim with two queries for the whole page, not one per row. */
    private List<DonorListing> map(List<Listing> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<Long> ids = page.stream().map(Listing::getId).toList();
        Map<Long, Claim> latest = claims.findByListingIdInOrderById(ids).stream()
                .collect(Collectors.toMap(Claim::getListingId, Function.identity(), (older, newer) -> newer));
        Map<Long, String> receiverNames = users.findAllById(latest.values().stream().map(Claim::getReceiverId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getName));
        return page.stream().map(listing -> {
            Claim claim = latest.get(listing.getId());
            ClaimBrief brief = claim == null ? null
                    : new ClaimBrief(claim.getId(), claim.number(), claim.getStatus(), receiverNames.get(claim.getReceiverId()),
                            ClaimMove.available(claim.getStatus(), Role.DONOR));
            return toDonorListing(listing, brief);
        }).toList();
    }

    private DonorListing toDonorListing(Listing l, ClaimBrief claim) {
        return new DonorListing(l.getId(), l.getTitle(), l.getQuantity(), l.getCategory(), Set.copyOf(l.getDietary()), l.getPickupFrom(),
                l.getPickupUntil(), l.getBestBefore(), l.getPickupAddress(), l.getPickupArea(), l.getTimeZone(), l.getStatus(),
                l.isHasPhoto(), claim, l.getClosedAt(), l.getCreatedAt());
    }
}
