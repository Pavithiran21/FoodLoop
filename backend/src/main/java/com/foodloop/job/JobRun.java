package com.foodloop.job;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The summary of one job run: counts only, never an email address, a name, a food item or a reason. */
@Entity
@Table(name = "job_runs")
public class JobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Column(nullable = false, length = 14)
    private String job;

    @Column(name = "trigger_type", nullable = false, length = 10)
    private String triggerType;

    @Column(nullable = false, length = 10)
    private String status;

    /** The moment the run worked for: real time, or the simulated time given to the job endpoint. */
    @Column(name = "as_of", nullable = false)
    private Instant asOf;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "items_seen", nullable = false)
    private int itemsSeen;

    @Column(name = "items_done", nullable = false)
    private int itemsDone;

    @Column(nullable = false)
    private int skipped;

    protected JobRun() {
    }

    public static JobRun start(String runId, String job, String triggerType, Instant asOf, Instant startedAt) {
        JobRun run = new JobRun();
        run.runId = runId;
        run.job = job;
        run.triggerType = triggerType;
        run.status = "RUNNING";
        run.asOf = asOf;
        run.startedAt = startedAt;
        return run;
    }

    public void finish(String finalStatus, JobRunSummary counts, Instant finishedAt) {
        this.status = finalStatus;
        this.finishedAt = finishedAt;
        this.itemsSeen = counts.itemsSeen();
        this.itemsDone = counts.itemsDone();
        this.skipped = counts.skipped();
    }
}
