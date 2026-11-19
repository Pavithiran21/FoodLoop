package com.foodloop.listing;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodloop.claim.ClaimRepository;
import com.foodloop.common.error.ApiException;
import com.foodloop.common.error.ErrorDetail;
import com.foodloop.user.CurrentUser;

import org.springframework.http.HttpStatus;

/**
 * Listing photos, stored in MySQL (listing_photos.data, LONGBLOB) next to the listing. The type is taken from the
 * file's first bytes (never from the filename or the Content-Type the browser sent) and the size is checked here
 * as well as by Spring's 5 MB multipart limit. A photo can be added, replaced or removed only while the listing is
 * LISTED and unclaimed. Who may download it is decided in {@link #download}.
 */
@Service
public class ListingPhotoService {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final String MESSAGE = "That file can't be used. Use a JPG or PNG photo up to 5 MB.";

    /** A photo ready to send: its stored type and its bytes. */
    public record Photo(String contentType, byte[] data) {
    }

    private final ListingRepository listings;
    private final ListingPhotoRepository photos;
    private final ClaimRepository claims;
    private final ListingService listingService;

    public ListingPhotoService(ListingRepository listings, ListingPhotoRepository photos, ClaimRepository claims,
            ListingService listingService) {
        this.listings = listings;
        this.photos = photos;
        this.claims = claims;
        this.listingService = listingService;
    }

    /** Adds or replaces the photo. */
    @Transactional
    public void put(CurrentUser actor, Long listingId, byte[] bytes) {
        actor.requireDonor();
        Listing listing = listingService.lockOwnUnclaimed(actor, listingId);
        String type = detectType(bytes);
        photos.save(new ListingPhoto(listingId, type, bytes));
        listing.setHasPhoto(true);
    }

    @Transactional
    public void remove(CurrentUser actor, Long listingId) {
        actor.requireDonor();
        Listing listing = listingService.lockOwnUnclaimed(actor, listingId);
        photos.deleteById(listingId);
        listing.setHasPhoto(false);
    }

    /**
     * Who may see a photo: the donor who made the listing, an Admin, the receiver who claimed it, and anyone logged
     * in while the listing is still on offer (it is shown on the public tickets anyway). Everyone else gets 404.
     */
    @Transactional(readOnly = true)
    public Photo download(CurrentUser actor, Long listingId) {
        Listing listing = listings.findById(listingId).filter(Listing::isHasPhoto).orElseThrow(() -> ApiException.notFound("Photo"));
        boolean allowed = actor.isAdmin()
                || (actor.isDonor() && listing.getDonorId().equals(actor.id()))
                || listing.getStatus() == ListingStatus.LISTED
                || (actor.isReceiver() && claims.existsByListingIdAndReceiverId(listingId, actor.id()));
        if (!allowed) {
            throw ApiException.notFound("Photo");
        }
        ListingPhoto photo = photos.findById(listingId).orElseThrow(() -> ApiException.notFound("Photo"));
        return new Photo(photo.getContentType(), photo.getData());
    }

    /** JPEG starts FF D8 FF; PNG starts 89 50 4E 47 0D 0A 1A 0A. Anything else, or an empty or oversized file, is a 400. */
    static String detectType(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw badPhoto();
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        byte[] png = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
        if (bytes.length >= png.length) {
            boolean isPng = true;
            for (int i = 0; i < png.length; i++) {
                isPng &= bytes[i] == png[i];
            }
            if (isPng) {
                return "image/png";
            }
        }
        throw badPhoto();
    }

    private static ApiException badPhoto() {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields are not valid.",
                List.of(new ErrorDetail("photo", MESSAGE)));
    }
}
