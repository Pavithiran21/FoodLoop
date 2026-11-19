package com.foodloop.job;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.foodloop.auth.TokenHashing;
import com.foodloop.common.error.ApiException;
import com.foodloop.config.AppProperties;

/**
 * Runs a job on demand: by hand (scripts/run-job.ps1), from tests, or from an external cron on a host that sleeps.
 * Only callers with the job secret may use it.
 *
 * Body {"at": "2026-11-12T03:40:00Z"} runs the job as if it were that moment, so tests and demos can check "the window
 * ended" and "24 hours after the hand-over" without waiting. It is accepted only when TIME_OVERRIDE_ENABLED=true,
 * which StartupConfigCheck refuses in production.
 */
@RestController
public class JobController {

    private final ExpiryService expiryService;
    private final AutoCompleteService autoCompleteService;
    private final AppProperties props;
    private final Clock clock;

    public JobController(ExpiryService expiryService, AutoCompleteService autoCompleteService, AppProperties props, Clock clock) {
        this.expiryService = expiryService;
        this.autoCompleteService = autoCompleteService;
        this.props = props;
        this.clock = clock;
    }

    public record RunJobRequest(Instant at) {
    }

    @PostMapping("/api/internal/jobs/expiry")
    public JobRunSummary expiry(@RequestHeader(name = "X-Job-Secret", required = false) String secret,
            @RequestBody(required = false) RunJobRequest body) {
        return expiryService.run(momentFor(secret, body), "API");
    }

    @PostMapping("/api/internal/jobs/auto-complete")
    public JobRunSummary autoComplete(@RequestHeader(name = "X-Job-Secret", required = false) String secret,
            @RequestBody(required = false) RunJobRequest body) {
        return autoCompleteService.run(momentFor(secret, body), "API");
    }

    private Instant momentFor(String secret, RunJobRequest body) {
        if (!TokenHashing.secretsMatch(secret, props.jobTriggerSecret())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "JOB_SECRET_INVALID", "The job secret is missing or wrong.");
        }
        Instant at = body == null ? null : body.at();
        if (at != null && !props.timeOverrideEnabled()) {
            throw ApiException.invalidField("at", "Running the job at another time is turned off on this server.");
        }
        return at != null ? at : clock.instant();
    }
}
