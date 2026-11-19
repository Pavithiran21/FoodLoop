package com.foodloop.auth;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodloop.auth.AuthDtos.LoginRequest;
import com.foodloop.common.error.ApiException;
import com.foodloop.common.web.RateLimiter;
import com.foodloop.common.web.RateLimiter.Limit;
import com.foodloop.user.User;
import com.foodloop.user.UserRepository;
import com.foodloop.user.UserStatus;

/**
 * Log in, refresh and log out. Sign-up is in {@link RegistrationService} (Donor and Receiver only; Admins come
 * from the seed script). Password reset is in {@link PasswordResetService}.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final SessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    /** Checked when the email is unknown, so a wrong email takes as long as a wrong password. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, SessionService sessions,
            PasswordEncoder passwordEncoder, RateLimiter rateLimiter, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("not-a-real-password-1");
    }

    /**
     * Not @Transactional on purpose: a BCrypt check takes about 0.3 s, and inside a transaction the request
     * would hold one of the 10 pooled database connections all that time, so a burst of log-ins (or someone
     * sending many at once) would leave every other request waiting for a connection. Each repository call
     * below uses its own short transaction; the refresh token is saved in one.
     */
    public AuthSession login(LoginRequest request, String clientIp) {
        rateLimiter.consume(Limit.LOGIN_IP, clientIp);
        String email = normalizeEmail(request.email());
        rateLimiter.ensureNotExhausted(Limit.LOGIN_FAILED_ACCOUNT, email);

        // A deleted account has no password any more: it looks like an unknown email, and the same time is
        // spent on the dummy hash.
        Optional<User> user = users.findByEmail(email).filter(found -> found.getPasswordHash() != null);
        String hash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            rateLimiter.recordFailure(Limit.LOGIN_FAILED_ACCOUNT, email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "That email or password doesn't match. Try again.");
        }
        if (user.get().getStatus() == UserStatus.SUSPENDED) {
            // Said only after the right password, so it doesn't reveal who has an account.
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED",
                    "Your account is suspended: " + user.get().getSuspendedReason());
        }
        return sessions.start(user.get());
    }

    /**
     * Swaps a refresh token for a new pair. The old row is deleted first and the delete must report
     * one row, so the same cookie can never be used twice, even by two requests at once.
     */
    @Transactional
    public AuthSession refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw sessionExpired();
        }
        RefreshToken stored = refreshTokens.findByTokenHash(TokenHashing.sha256Hex(refreshToken))
                .orElseThrow(AuthService::sessionExpired);
        if (refreshTokens.deleteByIdReturningCount(stored.getId()) != 1 || !stored.getExpiresAt().isAfter(clock.instant())) {
            throw sessionExpired();
        }
        User user = users.findById(stored.getUserId()).filter(User::isActive).orElseThrow(AuthService::sessionExpired);
        return sessions.start(user);
    }

    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokens.deleteByTokenHash(TokenHashing.sha256Hex(refreshToken));
        }
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException sessionExpired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Your session has ended. Log in again.");
    }
}
