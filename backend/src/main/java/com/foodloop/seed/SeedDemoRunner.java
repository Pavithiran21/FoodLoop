package com.foodloop.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Demo data for one demo (scripts\demo-up.ps1): the same Bengaluru sample as the dev seed ({@link SampleData}), in the
 * demo database. Refuses any database except "foodloop_demo", which demo-down drops again afterwards.
 */
@Component
@Profile("seed-demo")
public class SeedDemoRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDemoRunner.class);
    private static final String DEMO_DATABASE = "foodloop_demo";

    private final SeedSupport seed;
    private final SampleData sampleData;

    public SeedDemoRunner(SeedSupport seed, SampleData sampleData) {
        this.seed = seed;
        this.sampleData = sampleData;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed.runAndExit("Demo seed", this::seed, log);
    }

    private void seed() {
        seed.requireDatabase("seed-demo", DEMO_DATABASE);
        seed.requireDemoPassword();
        seed.wipeAll();
        sampleData.build();
        log.info("Demo seed done: the Bengaluru sample for the demo Donor, Receiver and Admin");
    }
}
