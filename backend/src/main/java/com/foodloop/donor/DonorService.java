package com.foodloop.donor;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodloop.common.error.ApiException;
import com.foodloop.donor.DonorDtos.UpdateDonorProfileRequest;
import com.foodloop.user.CurrentUser;
import com.foodloop.user.User;
import com.foodloop.user.UserRepository;

/** A donor's own business profile, and the check that only verified donors may list food. */
@Service
public class DonorService {

    private final DonorProfileRepository profiles;
    private final UserRepository users;
    private final Clock clock;

    public DonorService(DonorProfileRepository profiles, UserRepository users, Clock clock) {
        this.profiles = profiles;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public DonorProfile update(CurrentUser actor, UpdateDonorProfileRequest request) {
        actor.requireDonor();
        DonorProfile profile = profiles.findById(actor.id()).orElseThrow(() -> ApiException.notFound("Donor profile"));
        String fssai = request.fssaiNo() == null || request.fssaiNo().isBlank() ? null : request.fssaiNo();
        String zone = request.timeZone() == null ? profile.getTimeZone() : request.timeZone();
        // A new address applies to new listings only: every listing keeps the address it was made with.
        profile.update(request.businessName().trim(), fssai, request.pickupAddress().trim(), request.pickupArea().trim(),
                request.pickupLat(), request.pickupLng(), zone);
        User user = users.findById(actor.id()).orElseThrow(ApiException::unauthenticated);
        user.setName(request.contactName().trim());
        return profile;
    }

    /** A donor an Admin didn't verify asks to be looked at again: back to the waiting list. */
    @Transactional
    public DonorProfile requestReview(CurrentUser actor) {
        actor.requireDonor();
        DonorProfile profile = profiles.lockByUserId(actor.id()).orElseThrow(() -> ApiException.notFound("Donor profile"));
        if (profile.getVerificationStatus() != VerificationStatus.NOT_VERIFIED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "ILLEGAL_STATE",
                    "Only a donor account that wasn't verified can ask for another review.");
        }
        profile.requestReview(clock.instant());
        return profile;
    }

    /** The time zone of a donor's pick-up place, used to decide what "today" means on their board. */
    public ZoneId zoneOf(Long donorId) {
        return profiles.findById(donorId).map(p -> ZoneId.of(p.getTimeZone())).orElse(ZoneId.of(DonorDtos.DEFAULT_TIME_ZONE));
    }

    /** 403 for a donor who is waiting or was not verified. */
    public DonorProfile requireVerified(Long donorId) {
        DonorProfile profile = profiles.findById(donorId).orElseThrow(() -> ApiException.notFound("Donor profile"));
        if (!profile.isVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DONOR_NOT_VERIFIED", "Only verified donors can list food.");
        }
        return profile;
    }
}
