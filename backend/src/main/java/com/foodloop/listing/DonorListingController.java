package com.foodloop.listing;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.foodloop.common.web.PageResponse;
import com.foodloop.listing.ListingDtos.DonorListing;
import com.foodloop.listing.ListingDtos.ListingRequest;
import com.foodloop.listing.ListingService.BoardState;
import com.foodloop.listing.ListingService.Outcome;
import com.foodloop.user.CurrentUser;

import jakarta.validation.Valid;

/** A donor's own listings. Every method checks the Donor role and that the listing is theirs. */
@RestController
@RequestMapping("/api/donor/listings")
public class DonorListingController {

    private final ListingService listingService;
    private final ListingPhotoService photoService;

    public DonorListingController(ListingService listingService, ListingPhotoService photoService) {
        this.listingService = listingService;
        this.photoService = photoService;
    }

    @PostMapping
    public ResponseEntity<DonorListing> create(CurrentUser actor, @Valid @RequestBody ListingRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(listingService.create(actor, body));
    }

    /** One column of the board: state = LISTED, CLAIMED, READY, PICKED_UP or DONE_TODAY. */
    @GetMapping
    public PageResponse<DonorListing> board(CurrentUser actor, @RequestParam(defaultValue = "LISTED") BoardState state,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return listingService.board(actor, state, page, size);
    }

    @GetMapping("/history")
    public PageResponse<DonorListing> history(CurrentUser actor, @RequestParam(required = false) Outcome outcome,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return listingService.history(actor, outcome, page, size);
    }

    @GetMapping("/{id}")
    public DonorListing get(CurrentUser actor, @PathVariable Long id) {
        return listingService.get(actor, id);
    }

    @PutMapping("/{id}")
    public DonorListing update(CurrentUser actor, @PathVariable Long id, @Valid @RequestBody ListingRequest body) {
        return listingService.update(actor, id, body);
    }

    @PostMapping("/{id}/withdraw")
    public DonorListing withdraw(CurrentUser actor, @PathVariable Long id) {
        return listingService.withdraw(actor, id);
    }

    /** Multipart upload, field name "photo". Adds or replaces. */
    @PutMapping("/{id}/photo")
    public ResponseEntity<Void> putPhoto(CurrentUser actor, @PathVariable Long id, @RequestParam("photo") MultipartFile photo)
            throws IOException {
        photoService.put(actor, id, photo.getBytes());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/photo")
    public ResponseEntity<Void> deletePhoto(CurrentUser actor, @PathVariable Long id) {
        photoService.remove(actor, id);
        return ResponseEntity.noContent().build();
    }
}
