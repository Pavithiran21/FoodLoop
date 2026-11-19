package com.foodloop.admin;

import java.time.Clock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodloop.admin.AdminDtos.DonorCounts;
import com.foodloop.common.error.ApiException;
import com.foodloop.common.web.PageQuery;
import com.foodloop.common.web.PageResponse;
import com.foodloop.donor.DonorProfile;
import com.foodloop.donor.DonorProfileRepository;
import com.foodloop.donor.VerificationStatus;
import com.foodloop.mail.MailEvents.DonorNotVerified;
import com.foodloop.mail.MailEvents.DonorVerified;
import com.foodloop.user.CurrentUser;
import com.foodloop.user.User;
import com.foodloop.user.UserRepository;

/**
 * The Admin's donor queue: WAITING donors are verified or refused with a reason. The profile row is locked while the
 * Admin decides, and only a WAITING donor can be decided, so two Admins can't both act on the same request (the second
 * gets 422). The donor is emailed after the decision commits.
 */
@Service
public class AdminDonorService {

    private final DonorProfileRepository profiles;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AdminDonorService(DonorProfileRepository profiles, UserRepository users, ApplicationEventPublisher events, Clock clock) {
        this.profiles = profiles;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<DonorCard> list(CurrentUser actor, VerificationStatus status, Integer page, Integer size) {
        actor.requireAdmin();
        Pageable pageable = PageQuery.unsorted(page, size);
        Page<DonorCard> found = profiles.findCards(status == null ? VerificationStatus.WAITING : status, pageable);
        return new PageResponse<>(found.getContent(), found.getNumber() + 1, found.getSize(), found.getTotalElements(),
                found.getTotalPages());
    }

    @Transactional(readOnly = true)
    public DonorCounts counts(CurrentUser actor) {
        actor.requireAdmin();
        return new DonorCounts(profiles.countByVerificationStatus(VerificationStatus.WAITING),
                profiles.countByVerificationStatus(VerificationStatus.VERIFIED),
                profiles.countByVerificationStatus(VerificationStatus.NOT_VERIFIED));
    }

    @Transactional
    public void verify(CurrentUser actor, Long donorId) {
        actor.requireAdmin();
        DonorProfile profile = lockWaiting(donorId);
        profile.verify(actor.id(), clock.instant());
        events.publishEvent(new DonorVerified(donorId));
    }

    @Transactional
    public void reject(CurrentUser actor, Long donorId, String reason) {
        actor.requireAdmin();
        DonorProfile profile = lockWaiting(donorId);
        profile.reject(actor.id(), reason.trim());
        events.publishEvent(new DonorNotVerified(donorId));
    }

    private DonorProfile lockWaiting(Long donorId) {
        DonorProfile profile = profiles.lockByUserId(donorId).orElseThrow(() -> ApiException.notFound("Donor"));
        User user = users.findById(donorId).orElseThrow(() -> ApiException.notFound("Donor"));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "ILLEGAL_STATE", "That account isn't active.");
        }
        if (profile.getVerificationStatus() != VerificationStatus.WAITING) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "ILLEGAL_STATE",
                    "This donor isn't waiting for verification any more.");
        }
        return profile;
    }
}
