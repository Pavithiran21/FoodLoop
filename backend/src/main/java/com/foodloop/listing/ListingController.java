package com.foodloop.listing;

import java.util.Set;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foodloop.claim.ClaimDtos.ClaimResponse;
import com.foodloop.claim.ClaimService;
import com.foodloop.common.web.PageResponse;
import com.foodloop.listing.BrowseService.PickupWhen;
import com.foodloop.listing.ListingDtos.ListingCard;
import com.foodloop.listing.ListingDtos.ListingDetail;
import com.foodloop.listing.ListingPhotoService.Photo;
import com.foodloop.listing.ListingSearchRepository.SortBy;
import com.foodloop.report.ReportDtos.ReportRequest;
import com.foodloop.report.ReportService;
import com.foodloop.user.CurrentUser;

import jakarta.validation.Valid;

/** What a receiver does with listings: search, open one, claim it, report it. The photo is open to the people who may see it. */
@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final BrowseService browseService;
    private final ClaimService claimService;
    private final ReportService reportService;
    private final ListingPhotoService photoService;

    public ListingController(BrowseService browseService, ClaimService claimService, ReportService reportService,
            ListingPhotoService photoService) {
        this.browseService = browseService;
        this.claimService = claimService;
        this.reportService = reportService;
        this.photoService = photoService;
    }

    /** Find food. Without lat and lng the receiver's saved home location is the centre. */
    @GetMapping
    public PageResponse<ListingCard> search(CurrentUser actor,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) Set<Category> category,
            @RequestParam(required = false) Set<Dietary> dietary,
            @RequestParam(name = "when", required = false) PickupWhen when,
            @RequestParam(required = false) String timeZone,
            @RequestParam(defaultValue = "CLOSEST") SortBy sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return browseService.search(actor, lat, lng, radiusKm, category, dietary, when, timeZone, sort, page, size);
    }

    @GetMapping("/{id}")
    public ListingDetail get(CurrentUser actor, @PathVariable Long id, @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return browseService.detail(actor, id, lat, lng);
    }

    /** The photo bytes. A browser's img tag can't send the login header, so the frontend fetches this and shows a blob. */
    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(CurrentUser actor, @PathVariable Long id) {
        Photo photo = photoService.download(actor, id);
        return photoResponse(photo);
    }

    /** The signature endpoint: 201 for the one winner, 409 for everyone who was a moment too late. */
    @PostMapping("/{id}/claim")
    public ResponseEntity<ClaimResponse> claim(CurrentUser actor, @PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(claimService.claim(actor, id));
    }

    @PostMapping("/{id}/report")
    public ResponseEntity<Void> report(CurrentUser actor, @PathVariable Long id, @Valid @RequestBody ReportRequest body) {
        reportService.reportListing(actor, id, body);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    static ResponseEntity<byte[]> photoResponse(Photo photo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                // Private: the photo may only be for the claimant, so shared caches must not keep it.
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePrivate().getHeaderValue())
                .body(photo.data());
    }
}
