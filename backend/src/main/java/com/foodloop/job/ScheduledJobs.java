package com.foodloop.job;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the two jobs on EXPIRY_CRON (every minute by default) and AUTO_COMPLETE_CRON (every 10 minutes).
 * "-" turns a job off, which the test environment does so only the tests decide when a job runs.
 * Spring never starts a method again while its previous run is still going; an overlapping run from the job endpoint
 * is safe anyway (see ExpiryService and AutoCompleteService).
 */
@Component
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final ExpiryService expiryService;
    private final AutoCompleteService autoCompleteService;
    private final Clock clock;

    public ScheduledJobs(ExpiryService expiryService, AutoCompleteService autoCompleteService, Clock clock) {
        this.expiryService = expiryService;
        this.autoCompleteService = autoCompleteService;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.expiry-cron}", zone = "UTC")
    public void expire() {
        try {
            expiryService.run(clock.instant(), "SCHEDULE");
        } catch (RuntimeException ex) {
            log.error("Expiry job failed", ex);
        }
    }

    @Scheduled(cron = "${app.auto-complete-cron}", zone = "UTC")
    public void autoComplete() {
        try {
            autoCompleteService.run(clock.instant(), "SCHEDULE");
        } catch (RuntimeException ex) {
            log.error("Auto-complete job failed", ex);
        }
    }
}
