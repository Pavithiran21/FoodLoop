package com.foodloop.listing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodloop.claim.Claim;
import com.foodloop.claim.ClaimRepository;
import com.foodloop.common.error.ApiException;
import com.foodloop.common.web.Haversine;
import com.foodloop.common.web.PageQuery;
import com.foodloop.common.web.PageResponse;
import com.foodloop.donor.DonorProfile;
import com.foodloop.donor.DonorProfileRepository;
import com.foodloop.donor.DonorDtos;
import com.foodloop.listing.ListingDtos.DonorBrief;
import com.foodloop.listing.ListingDtos.ListingCard;
import com.foodloop.listing.ListingDtos.ListingDetail;
import com.foodloop.listing.ListingSearchRepository.Result;
import com.foodloop.listing.ListingSearchRepository.Search;
import com.foodloop.listing.ListingSearchRepository.SortBy;
import com.foodloop.report.ReportEnums.Kind;
import com.foodloop.report.ReportRepository;
import com.foodloop.user.CurrentUser;
import com.foodloop.user.User;
import com.foodloop.user.UserRepository;

/** What a receiver sees: Find food (search near a point) and one listing's page. Receivers only. */
@Service
public class BrowseService {

    /** The "Pick up" chips on Find food. */
    public enum PickupWhen {
        NEXT_2_HOURS,
        TODAY,
        TOMORROW
    }

    private static final double DEFAULT_RADIUS_KM = 5;
    private static final double MAX_RADIUS_KM = 50;

    private final ListingSearchRepository search;
    private final ListingRepository listings;
    private final DonorProfileRepository donorProfiles;
    private final ClaimRepository claims;
    private final ReportRepository reports;
    private final UserRepository users;
    private final Clock clock;

    public BrowseService(ListingSearchRepository search, ListingRepository listings, DonorProfileRepository donorProfiles,
            ClaimRepository claims, ReportRepository reports, UserRepository users, Clock clock) {
        this.search = search;
        this.listings = listings;
        this.donorProfiles = donorProfiles;
        this.claims = claims;
        this.reports = reports;
        this.users = users;
        this.clock = clock;
    }

    /**
     * The search centre is the lat/lng sent (an address the receiver chose), else their saved home location.
     * {@code timeZone} decides what "today" and "tomorrow" mean; it is the receiver's, sent by the browser.
     */
    @Transactional(readOnly = true)
    public PageResponse<ListingCard> search(CurrentUser actor, Double lat, Double lng, Double radiusKm, Set<Category> categories,
            Set<Dietary> dietary, PickupWhen when, String timeZone, SortBy sort, Integer page, Integer size) {
        actor.requireReceiver();
        Point centre = centre(actor, lat, lng);
        double radius = radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm;
        if (radius < 1 || radius > MAX_RADIUS_KM) {
            throw ApiException.invalidField("radiusKm", "Search within 1 to " + (int) MAX_RADIUS_KM + " km.");
        }
        Pageable paging = PageQuery.unsorted(page, size);   // same page and size checks as every list
        int pageNumber = paging.getPageNumber() + 1;
        int pageSize = paging.getPageSize();

        Instant now = clock.instant();
        Instant from = null;
        Instant to = null;
        if (when != null) {
            ZoneId zone = zone(timeZone);
            LocalDate today = now.atZone(zone).toLocalDate();
            switch (when) {
                case NEXT_2_HOURS -> {
                    from = now;
                    to = now.plus(Duration.ofHours(2));
                }
                case TODAY -> {
                    from = now;
                    to = today.plusDays(1).atStartOfDay(zone).toInstant();
                }
                case TOMORROW -> {
                    from = today.plusDays(1).atStartOfDay(zone).toInstant();
                    to = today.plusDays(2).atStartOfDay(zone).toInstant();
                }
            }
        }
        Result result = search.search(new Search(centre.lat(), centre.lng(), radius,
                categories == null ? EnumSet.noneOf(Category.class) : categories,
                dietary == null ? EnumSet.noneOf(Dietary.class) : dietary, from, to, now, sort == null ? SortBy.CLOSEST : sort),
                pageNumber, pageSize);
        int totalPages = (int) Math.ceil(result.total() / (double) pageSize);
        return new PageResponse<>(result.items(), pageNumber, pageSize, result.total(), totalPages);
    }

    /** One listing's page. The distance is shown when the request carries a location or the receiver has a home. */
    @Transactional(readOnly = true)
    public ListingDetail detail(CurrentUser actor, Long id, Double lat, Double lng) {
        actor.requireReceiver();
        Listing listing = listings.findById(id).orElseThrow(() -> ApiException.notFound("Listing"));
        DonorProfile donor = donorProfiles.findById(listing.getDonorId()).orElseThrow(() -> ApiException.notFound("Listing"));
        Double distance = null;
        Point centre = optionalCentre(actor, lat, lng);
        if (centre != null) {
            distance = Haversine.distanceKm(centre.lat(), centre.lng(), listing.getPickupLat(), listing.getPickupLng());
        }
        ListingCard card = new ListingCard(listing.getId(), listing.getTitle(), listing.getQuantity(), listing.getCategory(),
                Set.copyOf(listing.getDietary()), new DonorBrief(donor.getBusinessName(), donor.isVerified()),
                listing.getPickupArea(), distance, listing.getPickupFrom(), listing.getPickupUntil(), listing.getBestBefore(),
                listing.getTimeZone(), listing.isHasPhoto(), listing.getCreatedAt(),
                ListingCard.approx(listing.getPickupLat()), ListingCard.approx(listing.getPickupLng()));
        Long myClaimId = claims.findByListingIdOrderById(id).stream().filter(c -> c.getReceiverId().equals(actor.id()))
                .max(Comparator.comparing(Claim::getId)).map(Claim::getId).orElse(null);
        boolean reported = reports.existsByReporterIdAndKindAndTargetRef(actor.id(), Kind.LISTING, id);
        return new ListingDetail(card, listing.getStatus(), myClaimId, reported);
    }

    // ---- Helpers ----

    private record Point(double lat, double lng) {
    }

    private Point centre(CurrentUser actor, Double lat, Double lng) {
        Point point = optionalCentre(actor, lat, lng);
        if (point == null) {
            throw ApiException.invalidField("lat", "Choose a location to search near.");
        }
        return point;
    }

    private Point optionalCentre(CurrentUser actor, Double lat, Double lng) {
        if (lat != null && lng != null) {
            if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                throw ApiException.invalidField("lat", "That location isn't valid.");
            }
            return new Point(lat, lng);
        }
        User user = users.findById(actor.id()).orElseThrow(ApiException::unauthenticated);
        return user.hasHome() ? new Point(user.getHomeLat(), user.getHomeLng()) : null;
    }

    private static ZoneId zone(String timeZone) {
        try {
            return ZoneId.of(timeZone == null || timeZone.isBlank() ? DonorDtos.DEFAULT_TIME_ZONE : timeZone);
        } catch (RuntimeException ex) {
            throw ApiException.invalidField("timeZone", "Pick a time zone from the list, like Asia/Kolkata.");
        }
    }
}
