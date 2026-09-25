package com.satviklabs.runner;

import com.satviklabs.baseClasses.RetryAnalyzer;
import com.satviklabs.baseClasses.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Drives the full suite plus the retry loop in a single command.
 *
 * Called by the "retry" Maven profile:
 *
 *   mvn -Pretry test
 *
 * Algorithm
 * ---------
 *   1. Wipe any previous retry state (startFreshRun).
 *   2. Run the suite. The RetryListener records each scenario outcome.
 *   3. If scenarios are queued for rerun, re-invoke Maven selecting exactly
 *      those scenarios, with -Dretry.rerun=true so the ledger is preserved.
 *   4. Repeat until nothing is queued or no progress is made.
 *   5. Exit non-zero if any scenario is still failing after all attempts, so CI
 *      fails the build on a genuine defect - but a flaky pass does NOT fail it.
 *
 * Retry pass selection
 * -------------------
 * A normal run discovers features from the classpath. On a retry pass we want
 * ONLY the failed scenarios, and Cucumber supports that natively through a
 * rerun file (@path), which is what RetryAnalyzer writes to target/retry/rerun.txt.
 * Setting cucumber.features is safe there because that pass is defined entirely
 * by the rerun file - nothing else should be discovered.
 */
public final class RetrySuiteRunner {

    private static final Logger log = LoggerFactory.getLogger(RetrySuiteRunner.class);

    /** Bound so a pathological suite can never loop forever. */
    private static final int MAX_PASSES = 5;

    private RetrySuiteRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (!RetryConfig.active()) {
            log.info("Retry analyser disabled (RETRY_MAX_ATTEMPTS) - running once");
            int code = mvn("clean", "test", "-Dretry.rerun=false");
            System.exit(code);
        }

        log.info("Retry analyser: up to {} extra attempt(s) per failed scenario",
                RetryConfig.maxAttempts());

        RetryAnalyzer.startFreshRun();

        // Pass 0 - the normal, full run.
        int exit = mvn("clean", "test", "-Dretry.rerun=false");

        int pass = 0;
        while (pass++ < MAX_PASSES) {
            Set<String> queued = RetryAnalyzer.scenariosToRerun();
            if (queued.isEmpty()) {
                break;
            }
            log.warn("Retry pass {}: re-running {} scenario(s)", pass, queued.size());

            String rerunFile = Paths.get(RetryConfig.reportDir(), "rerun.txt")
                    .toAbsolutePath().toString();

            // Cucumber natively accepts a rerun file via the junit platform
            // configuration parameter cucumber.features, but setting it makes
            // Cucumber ignore classpath discovery for THAT pass only - which is
            // exactly what we want on a retry: run these scenarios and nothing else.
            int rerunExit = mvn("test",
                    "-Dretry.rerun=true",
                    "-Dcucumber.features=@" + rerunFile);

            if (rerunExit == 0) {
                exit = 0;
            }
        }

        // Determine the true outcome: only scenarios whose attempts are used up.
        List<String> stillFailing = RetryAnalyzer.stillFailing();
        List<String> flaky = RetryAnalyzer.flakyScenarios();

        log.info("=====================================================");
        log.info("Retry analyser final result");
        log.info("  Flaky scenarios (passed on retry): {}", flaky.size());
        flaky.forEach(s -> log.info("      FLAKY  {}", s));
        log.info("  Still failing after all attempts : {}", stillFailing.size());
        stillFailing.forEach(s -> log.info("      FAILED {}", s));
        log.info("  Retry report: {}{}retry-report.txt",
                RetryConfig.reportDir(), File.separator);
        log.info("=====================================================");

        RetryAnalyzer.flush();

        if (!stillFailing.isEmpty()) {
            System.exit(exit == 0 ? 1 : exit);
        }
        System.exit(0);
    }

    private static int mvn(String... goals) throws Exception {
        List<String> command = new ArrayList<>();
        String mvnExecutable = isWindows() ? "mvn.cmd" : "mvn";
        command.add(mvnExecutable);
        for (String goal : goals) {
            command.add(goal);
        }
        log.debug("Executing: {}", String.join(" ", command));

        ProcessBuilder builder = new ProcessBuilder(command).inheritIO();
        Process process = builder.start();
        return process.waitFor();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}

