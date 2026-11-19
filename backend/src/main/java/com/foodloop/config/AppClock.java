package com.foodloop.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The app's one clock: the system UTC clock, unless the current request asked for another "now".
 *
 * Tests need moments that real time can't give on demand, such as "15 minutes before the booking
 * starts" for check-in. When TIME_OVERRIDE_ENABLED=true (refused in production), a request can send
 * X-Test-Now and {@link com.foodloop.common.web.TestTimeFilter} sets it here for that request's thread
 * only, then clears it. Every service already asks this clock for "now", so none of them change, and
 * parallel requests never see each other's time.
 */
public class AppClock extends Clock {

    private static final ThreadLocal<Instant> OVERRIDE = new ThreadLocal<>();

    private final Clock system;

    private AppClock(Clock system) {
        this.system = system;
    }

    /** Sets "now" for the current thread (one request). Always paired with {@link #clearOverride()}. */
    public static void setOverride(Instant now) {
        OVERRIDE.set(now);
    }

    public static void clearOverride() {
        OVERRIDE.remove();
    }

    @Override
    public Instant instant() {
        Instant override = OVERRIDE.get();
        return override != null ? override : system.instant();
    }

    @Override
    public ZoneId getZone() {
        return system.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        // Keeps the override: LocalDate.now(clock.withZone(donorZone)) must see the same "now".
        return zone.equals(system.getZone()) ? this : new AppClock(system.withZone(zone));
    }

    /** A clock in UTC, for the bean in {@link ClockConfig}. */
    static AppClock utc() {
        return new AppClock(Clock.system(ZoneOffset.UTC));
    }
}
