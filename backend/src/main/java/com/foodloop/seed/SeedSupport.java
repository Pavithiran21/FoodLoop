package com.foodloop.seed;

import java.time.Instant;
import java.util.Set;

import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.foodloop.claim.Claim;
import com.foodloop.claim.ClaimRepository;
import com.foodloop.config.AppProperties;
import com.foodloop.donor.DonorProfile;
import com.foodloop.donor.DonorProfileRepository;
import com.foodloop.donor.VerificationStatus;
import com.foodloop.listing.Category;
import com.foodloop.listing.Dietary;
import com.foodloop.listing.Listing;
import com.foodloop.listing.ListingRepository;
import com.foodloop.listing.ListingStatus;
import com.foodloop.report.Report;
import com.foodloop.report.ReportRepository;
import com.foodloop.user.Role;
import com.foodloop.user.User;
import com.foodloop.user.UserRepository;

/**
 * What the seed runners share: the database guard, wiping the tables, and small helpers that build people, donors,
 * listings, claims and reports straight through the repositories. Seeded rows skip the app's rules on purpose (they include
 * past listings and claims in every state) and send no email.
 */
@Component
@Profile({ "seed-admin", "seed-dev", "seed-test", "seed-demo" })
public class SeedSupport {

    private static final String TIME_ZONE = "Asia/Kolkata";

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final DonorProfileRepository donorProfiles;
    private final ListingRepository listings;
    private final ClaimRepository claims;
    private final ReportRepository reports;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final ApplicationContext context;
    private String sharedHash;

    public SeedSupport(JdbcTemplate jdbc, UserRepository users, DonorProfileRepository donorProfiles, ListingRepository listings,
            ClaimRepository claims, ReportRepository reports, PasswordEncoder passwordEncoder, AppProperties props,
            ApplicationContext context) {
        this.jdbc = jdbc;
        this.users = users;
        this.donorProfiles = donorProfiles;
        this.listings = listings;
        this.claims = claims;
        this.reports = reports;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.context = context;
    }

    /**
     * Runs a seed once and stops the app (and its scheduler threads) afterwards, with exit code 1 if the seed failed, so
     * the calling script can stop too.
     */
    public void runAndExit(String what, Runnable seed, Logger log) {
        int exitCode = 0;
        try {
            seed.run();
        } catch (RuntimeException ex) {
            log.error("{} failed: {}", what, ex.getMessage());
            exitCode = 1;
        }
        int finalExitCode = exitCode;
        System.exit(SpringApplication.exit(context, () -> finalExitCode));
    }

    /** Refuses to seed any database other than the expected ones, so a seed can't touch the wrong data. */
    public void requireDatabase(String profile, String... expected) {
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (!Set.of(expected).contains(database)) {
            throw new IllegalStateException(profile + " only runs on " + String.join(" or ", expected) + ", not '" + database + "'");
        }
    }

    public void requireDemoPassword() {
        if (props.demoPassword() == null || props.demoPassword().length() < 8) {
            throw new IllegalStateException("Set DEMO_PASSWORD (at least 8 characters) in the env file");
        }
    }

    /** Empties every table, children first. Only called after requireDatabase. */
    public void wipeAll() {
        for (String table : new String[] { "reports", "claims", "listing_photos", "listing_dietary", "listings", "refresh_tokens",
                "password_reset_tokens", "donor_profiles", "users", "job_runs" }) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    /** BCrypt with 12 rounds takes 0.3 s, so every seeded person shares one hash of DEMO_PASSWORD. */
    public String demoHash() {
        if (sharedHash == null) {
            sharedHash = passwordEncoder.encode(props.demoPassword());
        }
        return sharedHash;
    }

    public User user(String name, String email, Role role, boolean demo) {
        User user = new User(name, email, demoHash(), role);
        user.setDemo(demo);
        return users.save(user);
    }

    public User receiver(String name, String email, boolean demo, String homeLabel, double lat, double lng) {
        User user = new User(name, email, demoHash(), Role.RECEIVER);
        user.setDemo(demo);
        user.setHome(homeLabel, lat, lng);
        return users.save(user);
    }

    /** A donor account plus its profile in the given verification state. */
    public User donor(String contact, String email, String business, VerificationStatus status, String reason, String fssai,
            String address, String area, double lat, double lng, boolean demo, User verifiedBy) {
        User user = user(contact, email, Role.DONOR, demo);
        DonorProfile profile = new DonorProfile(user.getId(), business, fssai, address, area, lat, lng, TIME_ZONE);
        if (status == VerificationStatus.VERIFIED) {
            profile.verify(verifiedBy == null ? null : verifiedBy.getId(), Instant.now());
        } else if (status == VerificationStatus.NOT_VERIFIED) {
            profile.reject(verifiedBy == null ? null : verifiedBy.getId(), reason);
        }
        donorProfiles.save(profile);
        return user;
    }

    /** A LISTED listing with the donor's current pick-up place; change its state with {@link #claim} or its own methods. */
    public Listing listing(User donor, String title, String quantity, Category category, Set<Dietary> dietary, Instant from,
            Instant until, Instant bestBefore) {
        DonorProfile p = donorProfiles.findById(donor.getId()).orElseThrow();
        return listings.save(new Listing(donor.getId(), title, quantity, category, dietary, from, until, bestBefore,
                p.getPickupAddress(), p.getPickupArea(), p.getPickupLat(), p.getPickupLng(), TIME_ZONE));
    }

    /**
     * The sample listings are inserted "now", but their claims are dated in the past, so the claim tracker would show
     * "Listed" after "Claimed". Runs last: each listing is dated 30 minutes before its pick-up starts (or keeps "now" if
     * that is earlier). No sample claim is made more than 30 minutes before its pick-up starts, so "Listed" comes first.
     */
    public void backdateListings() {
        jdbc.update("UPDATE listings SET created_at = LEAST(created_at, pickup_from - INTERVAL 30 MINUTE)");
    }

    /** Ends a listing without a claim: EXPIRED or WITHDRAWN at the given moment. */
    public Listing close(Listing listing, ListingStatus status, Instant at) {
        listing.moveTo(status, at);
        return listings.save(listing);
    }

    /**
     * A claim and the listing state that matches it. {@code at} is when it was claimed; later steps are a few minutes
     * apart, never after "now".
     */
    public Claim claim(Listing listing, User receiver, com.foodloop.claim.ClaimStatus status, Instant at, User cancelledBy) {
        Claim claim = claims.save(new Claim(listing.getId(), receiver.getId(), at));
        Instant step = at.plusSeconds(600);
        switch (status) {
            case CLAIMED -> listing.moveTo(ListingStatus.CLAIMED, at);
            case READY -> {
                claim.markReady(step);
                listing.moveTo(ListingStatus.READY, step);
            }
            case PICKED_UP -> {
                claim.markReady(step);
                claim.markPickedUp(step.plusSeconds(600));
                listing.moveTo(ListingStatus.PICKED_UP, step.plusSeconds(600));
            }
            case COMPLETED -> {
                claim.markReady(step);
                claim.markPickedUp(step.plusSeconds(600));
                claim.markCompleted(step.plusSeconds(1200));
                listing.moveTo(ListingStatus.COMPLETED, step.plusSeconds(1200));
            }
            case CANCELLED -> {
                claim.markCancelled(cancelledBy.getId(), step);
                listing.moveTo(ListingStatus.LISTED, step);
            }
            case EXPIRED -> {
                claim.markExpired(step);
                listing.moveTo(ListingStatus.EXPIRED, step);
            }
        }
        listings.save(listing);
        return claims.save(claim);
    }

    public Report save(Report report) {
        return reports.save(report);
    }

    public String demoPassword() {
        return props.demoPassword();
    }

    public PasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }

    public UserRepository users() {
        return users;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }
}
