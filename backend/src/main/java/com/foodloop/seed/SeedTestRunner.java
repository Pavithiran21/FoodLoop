package com.foodloop.seed;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.foodloop.donor.VerificationStatus;
import com.foodloop.user.Role;
import com.foodloop.user.User;

/**
 * The people the Playwright tests log in as (scripts\e2e.ps1 runs it before each pass). Tests create their own listings and
 * claims through the API; only what the API can't create quickly is seeded here:
 *  - three Admins (admin1..admin3@e2e.foodloop.test): Admins come only from a seed;
 *  - a pool of verified Donors (donor-0001..) and Receivers (rcv-0001..): a sign-up costs a 0.3 s BCrypt hash and a donor needs
 *    an Admin to verify them, which is too slow for the concurrency test's many receivers. Tests give each worker its own
 *    block of the pool, so tests running in parallel never share a person;
 *  - the three demo accounts (only with DEMO_MODE=true), for the /demo page and the demo-account locks.
 * Passwords: E2E_PASSWORD (only in .env.test) for the e2e people, DEMO_PASSWORD for the demo accounts.
 * Refuses any database except "foodloop_test".
 */
@Component
@Profile("seed-test")
public class SeedTestRunner implements ApplicationRunner {

    public static final int DONOR_POOL = 400;
    public static final int RECEIVER_POOL = 2000;

    private static final Logger log = LoggerFactory.getLogger(SeedTestRunner.class);
    private static final String TEST_DATABASE = "foodloop_test";
    private static final String DOMAIN = "@e2e.foodloop.test";
    private static final String HOME = "Indiranagar, Bengaluru, Karnataka, India";

    private final SeedSupport seed;
    private final JdbcTemplate jdbc;
    private final Environment env;

    public SeedTestRunner(SeedSupport seed, JdbcTemplate jdbc, Environment env) {
        this.seed = seed;
        this.jdbc = jdbc;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed.runAndExit("Test seed", this::seed, log);
    }

    private void seed() {
        seed.requireDatabase("seed-test", TEST_DATABASE);
        seed.requireDemoPassword();
        String password = env.getProperty("E2E_PASSWORD");
        if (password == null || password.length() < 8 || !password.matches(".*\\d.*")) {
            throw new IllegalStateException("Set E2E_PASSWORD (8+ characters with a number) in .env.test");
        }
        seed.wipeAll();

        // One hash for everyone: BCrypt with 12 rounds takes about 0.3 s, which 400 times would be two minutes.
        String hash = seed.passwordEncoder().encode(password);
        for (int i = 1; i <= 3; i++) {
            seed.users().save(new User("E2E Admin " + i, "admin" + i + DOMAIN, hash, Role.ADMIN));
        }
        insertPool(hash);

        boolean demoMode = env.getProperty("DEMO_MODE", Boolean.class, false);
        if (demoMode) {
            User admin = seed.user("Neha Kapoor", "admin" + SampleData.DOMAIN, Role.ADMIN, true);
            seed.donor("Asha Rao", "donor" + SampleData.DOMAIN, "Crumb & Co.", VerificationStatus.VERIFIED, null, null,
                    "100 Feet Road, Indiranagar, Bengaluru 560038", "Indiranagar", 12.9719, 77.6412, true, admin);
            seed.receiver("Meera N.", "receiver" + SampleData.DOMAIN, true, HOME, 12.9784, 77.6408);
        }
        log.info("Test seed done: 3 Admins, {} Donors, {} Receivers, {} demo accounts", DONOR_POOL, RECEIVER_POOL, demoMode ? 3 : 0);
    }

    /** Two batched INSERTs per table instead of 400 entity saves, so the seed takes seconds. */
    private void insertPool(String hash) {
        Timestamp now = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS));
        List<Object[]> donors = new ArrayList<>(DONOR_POOL);
        for (int i = 1; i <= DONOR_POOL; i++) {
            donors.add(new Object[] { "Test Donor " + pad(i), "donor-" + pad(i) + DOMAIN, hash, now, now });
        }
        jdbc.batchUpdate("INSERT INTO users (name, email, password_hash, role, status, is_demo, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'DONOR', 'ACTIVE', FALSE, ?, ?)", donors);

        List<Object[]> receivers = new ArrayList<>(RECEIVER_POOL);
        for (int i = 1; i <= RECEIVER_POOL; i++) {
            receivers.add(new Object[] { "Test Receiver " + pad(i), "rcv-" + pad(i) + DOMAIN, hash, HOME, 12.9784, 77.6408, now, now });
        }
        jdbc.batchUpdate("INSERT INTO users (name, email, password_hash, role, status, home_label, home_lat, home_lng, is_demo, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'RECEIVER', 'ACTIVE', ?, ?, ?, FALSE, ?, ?)", receivers);

        // Every pool donor gets a verified profile at the same Indiranagar address.
        List<Map<String, Object>> ids = jdbc.queryForList("SELECT id FROM users WHERE role = 'DONOR' AND email LIKE ?", "donor-%" + DOMAIN);
        List<Object[]> profiles = new ArrayList<>(ids.size());
        for (Map<String, Object> row : ids) {
            Long id = ((Number) row.get("id")).longValue();
            profiles.add(new Object[] { id, "Test Kitchen " + id, "100 Feet Road, Indiranagar, Bengaluru 560038", "Indiranagar", 12.9719,
                    77.6412, now, now, now });
        }
        jdbc.batchUpdate("INSERT INTO donor_profiles (user_id, business_name, pickup_address, pickup_area, pickup_lat, pickup_lng, time_zone, "
                + "verification_status, verified_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'Asia/Kolkata', 'VERIFIED', ?, ?, ?)", profiles);
    }

    private static String pad(int number) {
        return String.format("%04d", number);
    }
}
