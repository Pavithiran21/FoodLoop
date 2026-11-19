package com.foodloop.common.web;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.foodloop.common.error.RateLimitedException;
import com.foodloop.config.AppProperties;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;

/**
 * In-memory rate limits with Bucket4j: one token bucket per (limit, key), where the key is an IP
 * address or an email. Buckets live in a Caffeine cache that forgets idle keys, so memory stays
 * bounded. The log-in limits are the same as SubPulse's. RATE_LIMITS_ENABLED=false turns them off for
 * the main test pass (refused in production by StartupConfigCheck).
 *
 * In memory means per backend instance; fine for one instance, and noted in SECURITY.md.
 */
@Component
public class RateLimiter {

    public enum Limit {
        /** Sign-ups from one address: keeps scripts from filling the database with accounts. */
        REGISTER_IP(10, Duration.ofHours(1)),
        /** Address search: Nominatim's policy is about 1 request a second for the whole app, so one caller gets few. */
        PLACES_IP(30, Duration.ofMinutes(5)),
        /** Reports per person: stops a report storm against one account. */
        REPORT_USER(10, Duration.ofHours(1)),
        LOGIN_IP(20, Duration.ofMinutes(15)),
        /** Counts failed logins only, per account. */
        LOGIN_FAILED_ACCOUNT(5, Duration.ofMinutes(15)),
        FORGOT_PASSWORD(5, Duration.ofMinutes(15)),
        RESET_PASSWORD(10, Duration.ofMinutes(15));

        private final int capacity;
        private final Duration period;

        Limit(int capacity, Duration period) {
            this.capacity = capacity;
            this.period = period;
        }
    }

    private final boolean enabled;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS)
            .maximumSize(100_000)
            .build();

    public RateLimiter(AppProperties props) {
        this.enabled = props.rateLimitsEnabled();
    }

    /** Takes one attempt from the bucket, or throws 429 when it is empty. */
    public void consume(Limit limit, String key) {
        if (!enabled) {
            return;
        }
        ConsumptionProbe probe = bucket(limit, key).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new RateLimitedException(secondsToWait(probe.getNanosToWaitForRefill()));
        }
    }

    /** Throws 429 if the bucket is empty, without taking an attempt (used before checking a password). */
    public void ensureNotExhausted(Limit limit, String key) {
        if (!enabled) {
            return;
        }
        EstimationProbe probe = bucket(limit, key).estimateAbilityToConsume(1);
        if (!probe.canBeConsumed()) {
            throw new RateLimitedException(secondsToWait(probe.getNanosToWaitForRefill()));
        }
    }

    /** Records one failed attempt; never throws, so the caller can report the real failure. */
    public void recordFailure(Limit limit, String key) {
        if (enabled) {
            bucket(limit, key).tryConsume(1);
        }
    }

    private Bucket bucket(Limit limit, String key) {
        return buckets.get(limit.name() + ":" + key, ignored -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit.capacity)
                        .refillIntervally(limit.capacity, limit.period)
                        .build())
                .build());
    }

    private static long secondsToWait(long nanos) {
        return Math.max(1, TimeUnit.NANOSECONDS.toSeconds(nanos) + 1);
    }
}
