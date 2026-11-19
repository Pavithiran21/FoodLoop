package com.foodloop.user;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.foodloop.auth.AuthCookies;
import com.foodloop.auth.AuthSession;
import com.foodloop.user.ProfileDtos.ChangePasswordRequest;
import com.foodloop.user.ProfileDtos.DeleteAccountRequest;
import com.foodloop.user.ProfileDtos.PasswordChangedResponse;
import com.foodloop.user.ProfileDtos.UpdateProfileRequest;

import jakarta.validation.Valid;

/** The logged-in person's own account. There is no endpoint that takes another person's id. */
@RestController
@RequestMapping("/api/me")
public class ProfileController {

    private final ProfileService profileService;
    private final AuthCookies cookies;

    public ProfileController(ProfileService profileService, AuthCookies cookies) {
        this.profileService = profileService;
        this.cookies = cookies;
    }

    @GetMapping
    public UserResponse get(CurrentUser actor) {
        return profileService.get(actor);
    }

    @PatchMapping
    public UserResponse update(CurrentUser actor, @Valid @RequestBody UpdateProfileRequest body) {
        return profileService.update(actor, body);
    }

    @PostMapping("/password")
    public ResponseEntity<PasswordChangedResponse> changePassword(CurrentUser actor,
            @Valid @RequestBody ChangePasswordRequest body) {
        AuthSession session = profileService.changePassword(actor, body.currentPassword(), body.newPassword());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(session.refreshToken()))
                .body(new PasswordChangedResponse(session.accessToken()));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(CurrentUser actor, @Valid @RequestBody DeleteAccountRequest body) {
        profileService.deleteAccount(actor, body.password());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }
}
