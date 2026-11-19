package com.foodloop.job;

import java.time.Instant;

/**
 * What a job run returns and stores. itemsSeen: listings or claims that were due. itemsDone: those this run handled
 * (expired, or completed). skipped: due items another run (or a person) handled first. The emails are sent after the
 * run, in the background (MailEventListener), so a failed send is logged by MailService and not counted here.
 */
public record JobRunSummary(String runId, String job, Instant asOf, int itemsSeen, int itemsDone, int skipped) {
}
