package com.foodloop.job;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.foodloop.claim.Claim;
import com.foodloop.claim.ClaimRepository;
import com.foodloop.common.DeadlockRetry;
import com.foodloop.listing.ListingRepository;
import com.foodloop.mail.MailEvents.ClaimCompleted;

/**
 * Auto-complete: a claim that was picked up at least 24 hours ago and that the receiver never confirmed completes by
 * itself, and both sides are emailed once. Same idempotency as {@link ExpiryService}: one UPDATE ... WHERE id = ? AND
 * status = 'PICKED_UP', and only the run that gets "1 row changed" publishes the email event. A receiver pressing
 * "I got it" at the same moment holds the claim lock, so the UPDATE waits and then finds the claim COMPLETED (skipped).
 */
@Service
public class AutoCompleteService {

    public static final Duration AFTER = Duration.ofHours(24);
    private static final int PAGE_SIZE = 200;
    private static final Logger log = LoggerFactory.getLogger(AutoCompleteService.class);

    private final ClaimRepository claims;
    private final ListingRepository listings;
    private final JobRunRepository jobRuns;
    private final ApplicationEventPublisher events;
    private final DeadlockRetry retry;
    private final Clock clock;

    public AutoCompleteService(ClaimRepository claims, ListingRepository listings, JobRunRepository jobRuns,
            ApplicationEventPublisher events, DeadlockRetry retry, Clock clock) {
        this.claims = claims;
        this.listings = listings;
        this.jobRuns = jobRuns;
        this.events = events;
        this.retry = retry;
        this.clock = clock;
    }

    public JobRunSummary run(Instant now, String trigger) {
        String runId = UUID.randomUUID().toString();
        JobRun jobRun = jobRuns.save(JobRun.start(runId, "AUTO_COMPLETE", trigger, now, clock.instant()));
        JobCounters counters = new JobCounters();
        String finalStatus = "FAILED";
        try {
            Instant cutoff = now.minus(AFTER);
            long afterId = 0;
            List<Claim> page = claims.findDueForAutoComplete(cutoff, afterId, Limit.of(PAGE_SIZE));
            while (!page.isEmpty()) {
                for (Claim claim : page) {
                    complete(claim, now, counters);
                }
                afterId = page.get(page.size() - 1).getId();
                page = claims.findDueForAutoComplete(cutoff, afterId, Limit.of(PAGE_SIZE));
            }
            finalStatus = "FINISHED";
        } finally {
            JobRunSummary summary = counters.toSummary(runId, "AUTO_COMPLETE", now);
            jobRun.finish(finalStatus, summary, clock.instant());
            jobRuns.save(jobRun);
            if (summary.itemsDone() > 0 || "FAILED".equals(finalStatus)) {
                log.info("Auto-complete job {} {} (as of {}): due={}, completed={}, skipped={}", runId, finalStatus.toLowerCase(),
                        now, summary.itemsSeen(), summary.itemsDone(), summary.skipped());
            }
        }
        return counters.toSummary(runId, "AUTO_COMPLETE", now);
    }

    private void complete(Claim claim, Instant now, JobCounters counters) {
        counters.seen++;
        boolean completed = retry.inTransaction(() -> {
            if (claims.autoCompleteIfPickedUp(claim.getId(), now, touched()) != 1) {
                return false;
            }
            listings.completeIfPickedUp(claim.getListingId(), now, touched());
            events.publishEvent(new ClaimCompleted(claim.getId()));   // sent after this transaction commits
            return true;
        });
        if (completed) {
            counters.done++;
        } else {
            counters.skipped++;   // the receiver confirmed first
        }
    }

    private static Instant touched() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
