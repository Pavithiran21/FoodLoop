package com.foodloop.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodloop.auth.AuthSession;
import com.foodloop.auth.PasswordResetTokenRepository;
import com.foodloop.auth.RefreshTokenRepository;
import com.foodloop.auth.SessionService;
import com.foodloop.claim.Claim;
import com.foodloop.claim.ClaimRepository;
import com.foodloop.claim.ClaimService;
import com.foodloop.claim.ClaimStatus;
import com.foodloop.common.DeadlockRetry;
import com.foodloop.common.error.ApiException;
import com.foodloop.common.error.ErrorDetail;
import com.foodloop.donor.DonorProfileRepository;
import com.foodloop.listing.ListingPhotoRepository;
import com.foodloop.listing.ListingRepository;
import com.foodloop.listing.ListingService;
import com.foodloop.user.ProfileDtos.UpdateProfileRequest;

/** The logged-in person's own profile, password and account deletion. */
@Service
public class ProfileService {

    private final UserRepository users;
    private final DonorProfileRepository donorProfiles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final ClaimRepository claims;
    private final ClaimService claimService;
    private final ListingService listingService;
    private final ListingRepository listings;
    private final ListingPhotoRepository photos;
    private final SessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final DeadlockRetry retry;

    public ProfileService(UserRepository users, DonorProfileRepository donorProfiles, RefreshTokenRepository refreshTokens,
            PasswordResetTokenRepository resetTokens, ClaimRepository claims, ClaimService claimService,
            ListingService listingService, ListingRepository listings, ListingPhotoRepository photos, SessionService sessions,
            PasswordEncoder passwordEncoder, DeadlockRetry retry) {
        this.users = users;
        this.donorProfiles = donorProfiles;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.claims = claims;
        this.claimService = claimService;
        this.listingService = listingService;
        this.listings = listings;
        this.photos = photos;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.retry = retry;
    }

    @Transactional(readOnly = true)
    public UserResponse get(CurrentUser actor) {
        return sessions.profileOf(load(actor));
    }

    /** Name for everyone; the home location for receivers (a donor's address lives in the donor profile). */
    @Transactional
    public UserResponse update(CurrentUser actor, UpdateProfileRequest request) {
        User user = load(actor);
        user.setName(request.name().trim());
        if (user.getRole() == Role.RECEIVER) {
            boolean any = request.homeLabel() != null && !request.homeLabel().isBlank();
            if (any && (request.homeLat() == null || request.homeLng() == null)) {
                throw ApiException.invalidField("homeLabel", "Find your area again, or leave it empty.");
            }
            user.setHome(any ? request.homeLabel().trim() : null, any ? request.homeLat() : null, any ? request.homeLng() : null);
        }
        return sessions.profileOf(user);
    }

    /** Logs out every other browser and returns a new login for this one. */
    @Transactional
    public AuthSession changePassword(CurrentUser actor, String currentPassword, String newPassword) {
        User user = load(actor);
        ensureNotDemo(user, "Demo accounts can't change the password.");
        checkPassword(user, currentPassword, "currentPassword");
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.deleteAllForUser(user.getId());
        return sessions.start(user);
    }

    /**
     * Deletes the account by anonymising it: the name becomes "Former user" (or "Former donor"), the email an unusable
     * address, the password and every token are removed, and open claims are cancelled (the other side is emailed). A
     * donor's listings are withdrawn and their photos and pick-up addresses removed. The rows stay, so the other side's
     * claims and History still read. Admins can't delete themselves (they come only from the seed script).
     * One transaction, run again by {@link DeadlockRetry} if it loses a deadlock against the other side's claim move.
     */
    public void deleteAccount(CurrentUser actor, String password) {
        retry.inTransaction(() -> deleteAccountInTransaction(actor, password));
    }

    private void deleteAccountInTransaction(CurrentUser actor, String password) {
        User user = users.lockById(actor.id()).filter(User::isActive).orElseThrow(ApiException::unauthenticated);
        ensureNotDemo(user, "Demo accounts can't be deleted.");
        if (user.getRole() == Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin accounts can't be deleted here.");
        }
        checkPassword(user, password, "password");

        // Claims first: cancelling puts a donor's listing back on the list, and withdrawing afterwards takes it off.
        List<Claim> open = user.getRole() == Role.DONOR
                ? claims.findActiveForDonor(user.getId(), ClaimStatus.BEFORE_PICKUP)
                : claims.findByReceiverIdAndStatusIn(user.getId(), ClaimStatus.BEFORE_PICKUP);
        for (Claim claim : open) {
            claimService.cancelForAccountChange(claim.getId(), user.getId(), user.getId());
        }
        String formerName = user.getRole() == Role.DONOR ? User.FORMER_DONOR : User.FORMER_USER;
        if (user.getRole() == Role.DONOR) {
            listingService.withdrawOpenListings(user.getId());
            photos.deleteAllForDonor(user.getId());
            listings.anonymiseDonorListings(user.getId());
            donorProfiles.findById(user.getId()).ifPresent(profile -> profile.anonymise(formerName));
        }
        refreshTokens.deleteAllForUser(user.getId());
        resetTokens.deleteAllForUser(user.getId());
        user.anonymise(formerName);
    }

    private User load(CurrentUser actor) {
        return users.findById(actor.id()).filter(User::isActive).orElseThrow(ApiException::unauthenticated);
    }

    private static void ensureNotDemo(User user, String message) {
        if (user.isDemo()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DEMO_ACCOUNT_LOCKED", message);
        }
    }

    /** 400, not 401: the frontend treats any 401 as "logged out". */
    private void checkPassword(User user, String password, String field) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "That password isn't right.",
                    List.of(new ErrorDetail(field, "That password isn't right.")));
        }
    }
}
