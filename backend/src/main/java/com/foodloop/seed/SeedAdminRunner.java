package com.foodloop.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.foodloop.auth.AuthService;
import com.foodloop.user.Role;
import com.foodloop.user.User;

/**
 * Creates an Admin. Admins are never created through the app: public sign-up offers only Donor and Receiver. Run with
 * scripts\seed.ps1 -Seed admin -Email you@example.com -Name "Your Name"; the password is ADMIN_PASSWORD in the env file or
 * the environment (8 to 72 characters with a number). It works on any database, including production, and never
 * overwrites an existing account.
 */
@Component
@Profile("seed-admin")
public class SeedAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedAdminRunner.class);

    private final SeedSupport seed;
    private final Environment env;

    public SeedAdminRunner(SeedSupport seed, Environment env) {
        this.seed = seed;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed.runAndExit("Admin seed", () -> createAdmin(args), log);
    }

    private void createAdmin(ApplicationArguments args) {
        String email = option(args, "email");
        String name = option(args, "name");
        String password = env.getProperty("ADMIN_PASSWORD");
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Pass --email you@example.com");
        }
        if (name == null || name.isBlank() || name.length() > 80) {
            throw new IllegalArgumentException("Pass --name \"Your Name\" (1 to 80 characters)");
        }
        if (password == null || password.length() < 8 || !password.matches(".*\\d.*") || password.length() > 72) {
            throw new IllegalArgumentException("Set ADMIN_PASSWORD (8 to 72 characters, with a number)");
        }
        String normalized = AuthService.normalizeEmail(email);
        if (seed.users().existsByEmail(normalized)) {
            throw new IllegalStateException("An account with that email already exists");
        }
        seed.users().save(new User(name.trim(), normalized, seed.passwordEncoder().encode(password), Role.ADMIN));
        log.info("Admin created (log in with that email and ADMIN_PASSWORD)");
    }

    private static String option(ApplicationArguments args, String name) {
        return args.containsOption(name) && !args.getOptionValues(name).isEmpty() ? args.getOptionValues(name).get(0) : null;
    }
}
