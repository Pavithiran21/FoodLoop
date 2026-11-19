package com.foodloop.auth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.foodloop.auth.AuthDtos.RegisterRequest;
import com.foodloop.auth.AuthDtos.SignUpRole;
import com.foodloop.common.error.ApiException;
import com.foodloop.common.error.DatabaseErrors;
import com.foodloop.common.error.ErrorDetail;
import com.foodloop.common.web.RateLimiter;
import com.foodloop.common.web.RateLimiter.Limit;
import com.foodloop.donor.DonorDtos;
import com.foodloop.donor.DonorProfile;
import com.foodloop.donor.DonorProfileRepository;
import com.foodloop.user.Role;
import com.foodloop.user.User;
import com.foodloop.user.UserRepository;

/**
 * Public sign-up for Donors and Receivers. A Donor starts as WAITING and can list only after an Admin verifies
 * them; a Receiver can claim straight away. The user and the donor profile are saved in one transaction, and the
 * BCrypt hash is made before it starts so no database connection is held during the 0.3 s hash.
 */
@Service
public class RegistrationService {

    private final UserRepository users;
    private final DonorProfileRepository donorProfiles;
    private final SessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final TransactionTemplate transaction;

    public RegistrationService(UserRepository users, DonorProfileRepository donorProfiles, SessionService sessions,
            PasswordEncoder passwordEncoder, RateLimiter rateLimiter, TransactionTemplate transaction) {
        this.users = users;
        this.donorProfiles = donorProfiles;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.transaction = transaction;
    }

    public AuthSession register(RegisterRequest request, String clientIp) {
        rateLimiter.consume(Limit.REGISTER_IP, clientIp);
        checkRoleFields(request);
        String email = AuthService.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        String hash = passwordEncoder.encode(request.password());
        try {
            User user = transaction.execute(status -> save(request, email, hash));
            return sessions.start(user);
        } catch (DataIntegrityViolationException ex) {
            if (DatabaseErrors.isDuplicateKey(ex)) {
                throw emailTaken(); // two sign-ups with one email at the same moment
            }
            throw ex;
        }
    }

    private User save(RegisterRequest request, String email, String hash) {
        boolean donor = request.role() == SignUpRole.DONOR;
        User user = new User(request.name().trim(), email, hash, donor ? Role.DONOR : Role.RECEIVER);
        if (!donor && request.homeLabel() != null && !request.homeLabel().isBlank()) {
            user.setHome(request.homeLabel().trim(), request.homeLat(), request.homeLng());
        }
        users.save(user);
        if (donor) {
            String fssai = request.fssaiNo() == null || request.fssaiNo().isBlank() ? null : request.fssaiNo();
            String zone = request.timeZone() == null ? DonorDtos.DEFAULT_TIME_ZONE : request.timeZone();
            donorProfiles.save(new DonorProfile(user.getId(), request.businessName().trim(), fssai,
                    request.pickupAddress().trim(), request.pickupArea().trim(), request.pickupLat(), request.pickupLng(), zone));
        }
        return user;
    }

    /** The role-specific rules, reported field by field in the shared 400 format. */
    private static void checkRoleFields(RegisterRequest request) {
        List<ErrorDetail> problems = new ArrayList<>();
        if (request.role() == SignUpRole.DONOR) {
            if (isBlank(request.businessName())) {
                problems.add(new ErrorDetail("businessName", "Enter the business name."));
            }
            if (isBlank(request.pickupAddress()) || isBlank(request.pickupArea()) || request.pickupLat() == null
                    || request.pickupLng() == null) {
                problems.add(new ErrorDetail("pickupAddress", "Find the pick-up address."));
            }
        } else if (!isBlank(request.homeLabel()) && (request.homeLat() == null || request.homeLng() == null)) {
            problems.add(new ErrorDetail("homeLabel", "Find your area again, or leave it empty."));
        }
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields are not valid.", problems);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "That email already has an account. Log in instead.",
                List.of(new ErrorDetail("email", "That email already has an account. Log in instead.")));
    }
}
