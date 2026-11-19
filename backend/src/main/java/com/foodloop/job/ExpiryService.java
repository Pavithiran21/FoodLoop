package com.foodloop.job;

import java.time.Clock;
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
import com.foodloop.listing.Listing;
import com.foodloop.listing.ListingRepository;
import com.foodloop.mail.MailEvents.ClaimExpired;
import com.foodloop.mail.MailEvents.ListingExpired;

/**
 * Expiry: a listing or claim still open when its pick-up window ends is closed as EXPIRED, and the people involved are
 * emailed once. A run does two passes:
 *  1. LISTED listings whose window has ended (nobody claimed them) become EXPIRED; the donor is emailed.
 *  2. CLAIMED or READY claims whose window has ended become EXPIRED together with their listing; both sides are
 *     emailed. A PICKED_UP claim is not expired: the food was handed over, and it completes by itself later.
 *
 * Why it is safe to run twice, or two runs at once (the schedule and the job endpoint), or while a person moves the
 * same claim: closing is one UPDATE ... WHERE id = ? AND status = 'LISTED' (or IN ('CLAIMED','READY')), and only the run
 * that gets "1 row changed" asks for the email. A donor marking the claim ready at the same moment holds the claim row
 * lock, so the UPDATE waits and then re-checks the status. Claim first, then listing: the same lock order as
 * ClaimService.
 *
 * Not @Transactional as a whole on purpose: each item commits by itself, so one failure doesn't undo the rest. Each item
 * runs through {@link DeadlockRetry}: a status change also locks the users row (foreign-key check), so an account
 * suspension at the same moment can deadlock with it.
 */
@Service
public class ExpiryService {

    private static final int PAGE_SIZE = 200;
    private static final Logger log = LoggerFactory.getLogger(ExpiryService.class);

    private final ListingRepository listings;
    private final ClaimRepository claims;
    private final JobRunRepository jobRuns;
    private final ApplicationEventPublisher events;
    private final DeadlockRetry retry;
    private final Clock clock;

    public ExpiryService(ListingRepository listings, ClaimRepository claims, JobRunRepository jobRuns,
            ApplicationEventPublisher events, DeadlockRetry retry, Clock clock) {
        this.listings = listings;
        this.claims = claims;
        this.jobRuns = jobRuns;
        this.events = events;
        this.retry = retry;
        this.clock = clock;
    }

    public JobRunSummary run(Instant now, String trigger) {
        String runId = UUID.randomUUID().toString();
        JobRun jobRun = jobRuns.save(JobRun.start(runId, "EXPIRY", trigger, now, clock.instant()));
        JobCounters counters = new JobCounters();
        String finalStatus = "FAILED";
        try {
            expireUnclaimedListings(now, counters);
            expireOpenClaims(now, counters);
            finalStatus = "FINISHED";
        } finally {
            JobRunSummary summary = counters.toSummary(runId, "EXPIRY", now);
            jobRun.finish(finalStatus, summary, clock.instant());
            jobRuns.save(jobRun);
            if (summary.itemsDone() > 0 || "FAILED".equals(finalStatus)) {
                log.info("Expiry job {} {} (as of {}): due={}, expired={}, skipped={}", runId, finalStatus.toLowerCase(), now,
                        summary.itemsSeen(), summary.itemsDone(), summary.skipped());
            }
        }
        return counters.toSummary(runId, "EXPIRY", now);
    }

    private void expireUnclaimedListings(Instant now, JobCounters counters) {
        long afterId = 0;
        List<Listing> page = listings.findDueForExpiry(now, afterId, Limit.of(PAGE_SIZE));
        while (!page.isEmpty()) {
            for (Listing listing : page) {
                counters.seen++;
                boolean expired = retry.inTransaction(() -> {
                    if (listings.expireIfListed(listing.getId(), now, touched()) != 1) {
                        return false;
                    }
                    events.publishEvent(new ListingExpired(listing.getId()));   // sent after this transaction commits
                    return true;
                });
                if (expired) {
                    counters.done++;
                } else {
                    counters.skipped++;   // claimed, withdrawn or edited into a later window since we listed it
                }
            }
            afterId = page.get(page.size() - 1).getId();
            page = listings.findDueForExpiry(now, afterId, Limit.of(PAGE_SIZE));
        }
    }

    private void expireOpenClaims(Instant now, JobCounters counters) {
        long afterId = 0;
        List<Claim> page = claims.findDueForExpiry(now, afterId, Limit.of(PAGE_SIZE));
        while (!page.isEmpty()) {
            for (Claim claim : page) {
                counters.seen++;
                boolean expired = retry.inTransaction(() -> {
                    if (claims.expireIfOpen(claim.getId(), now, touched()) != 1) {
                        return false;
                    }
                    listings.expireIfClaimed(claim.getListingId(), now, touched());
                    events.publishEvent(new ClaimExpired(claim.getId()));   // sent after this transaction commits
                    return true;
                });
                if (expired) {
                    counters.done++;
                } else {
                    counters.skipped++;   // picked up, cancelled or expired by someone else since we listed it
                }
            }
            afterId = page.get(page.size() - 1).getId();
            page = claims.findDueForExpiry(now, afterId, Limit.of(PAGE_SIZE));
        }
    }

    private static Instant touched() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
