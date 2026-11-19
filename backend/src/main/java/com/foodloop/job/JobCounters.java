package com.foodloop.job;

import java.time.Instant;

/** Running totals for one job run. */
final class JobCounters {

    int seen;
    int done;
    int skipped;

    JobRunSummary toSummary(String runId, String job, Instant asOf) {
        return new JobRunSummary(runId, job, asOf, seen, done, skipped);
    }
}
