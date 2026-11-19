package com.foodloop.donor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.foodloop.auth.SessionService;
import com.foodloop.donor.DonorDtos.UpdateDonorProfileRequest;
import com.foodloop.user.CurrentUser;
import com.foodloop.user.UserResponse;
import com.foodloop.user.UserResponse.DonorInfo;

import jakarta.validation.Valid;

/** The Donor's business profile (the Profile screen and the "Not verified" banner). */
@RestController
@RequestMapping("/api/donor/profile")
public class DonorController {

    private final DonorService donorService;
    private final SessionService sessions;

    public DonorController(DonorService donorService, SessionService sessions) {
        this.donorService = donorService;
        this.sessions = sessions;
    }

    @GetMapping
    public DonorInfo get(CurrentUser actor) {
        actor.requireDonor();
        return sessions.donorInfo(actor.id());
    }

    @PatchMapping
    public UserResponse update(CurrentUser actor, @Valid @RequestBody UpdateDonorProfileRequest body) {
        donorService.update(actor, body);
        return sessions.profileOfId(actor.id());
    }

    @PostMapping("/review-request")
    public UserResponse requestReview(CurrentUser actor) {
        donorService.requestReview(actor);
        return sessions.profileOfId(actor.id());
    }
}
