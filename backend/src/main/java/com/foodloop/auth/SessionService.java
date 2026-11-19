package com.foodloop.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.foodloop.donor.DonorProfile;
import com.foodloop.donor.DonorProfileRepository;
import com.foodloop.user.Role;
import com.foodloop.user.User;
import com.foodloop.user.UserRepository;
import com.foodloop.user.UserResponse;
import com.foodloop.user.UserResponse.DonorInfo;
import com.foodloop.user.UserResponse.Home;

/** Starts a login for a user: a new refresh token row plus a 15-minute access token. Also builds the profile body. */
@Service
public class SessionService {

    public static final Duration REFRESH_LIFETIME = Duration.ofDays(7);

    private final RefreshTokenRepository refreshTokens;
    private final AccessTokenService accessTokens;
    private final UserRepository users;
    private final DonorProfileRepository donorProfiles;
    private final Clock clock;

    public SessionService(RefreshTokenRepository refreshTokens, AccessTokenService accessTokens, UserRepository users,
            DonorProfileRepository donorProfiles, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.users = users;
        this.donorProfiles = donorProfiles;
        this.clock = clock;
    }

    public AuthSession start(User user) {
        String refreshToken = TokenHashing.newOpaqueToken();
        Instant now = clock.instant();
        refreshTokens.save(new RefreshToken(user.getId(), TokenHashing.sha256Hex(refreshToken), now, now.plus(REFRESH_LIFETIME)));
        return new AuthSession(profileOf(user), accessTokens.create(user.getId()), refreshToken);
    }

    public UserResponse profileOf(User user) {
        Home home = user.hasHome() ? new Home(user.getHomeLabel(), user.getHomeLat(), user.getHomeLng()) : null;
        DonorInfo donor = user.getRole() == Role.DONOR ? donorInfo(user.getId()) : null;
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isDemo(), home, donor);
    }

    public UserResponse profileOfId(Long userId) {
        return profileOf(users.findById(userId).orElseThrow(com.foodloop.common.error.ApiException::unauthenticated));
    }

    public DonorInfo donorInfo(Long userId) {
        return donorProfiles.findById(userId).map(SessionService::toInfo).orElse(null);
    }

    private static DonorInfo toInfo(DonorProfile p) {
        return new DonorInfo(p.getBusinessName(), p.getFssaiNo(), p.getPickupAddress(), p.getPickupArea(), p.getPickupLat(),
                p.getPickupLng(), p.getTimeZone(), p.getVerificationStatus(), p.getRejectionReason());
    }
}
