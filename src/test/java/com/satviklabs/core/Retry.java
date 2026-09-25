package com.satviklabs.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.satviklabs.baseClasses.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single home for the Cucumber scenario-retry mechanism: its configuration and
 * the ledger that tracks attempts across passes.
 *
 * The listener that feeds outcomes in lives in {@code RetryListener} alongside
 * this class, because the JUnit Platform only auto-registers listeners declared
 * as top-level classes via the ServiceLoader.
 */
public final class Retry {

    private Retry() {
    }

    /**
     * Configuration for the retry analyser.
     *
     * Every value resolves through {@link ConfigLoader}, so it can be overridden on
     * the command line (-DRETRY_MAX_ATTEMPTS=2), in the environment, or in .env /
     * config.properties exactly like every other setting.
     *
     * RETRY_ENABLED (default true) master on/off switch
     * RETRY_MAX_ATTEMPTS (default 1) extra runs per failed scenario
     * RETRY_TAG_EXCLUDE (default @no-retry) scenarios that must never retry
     * RETRY_REPORT_DIR (default target/retry) where the ledger is written
     */
    public static final class Config {

        private Config() {
        }

        /** Whether failed scenarios may be retried at all. */
        public static boolean enabled() {
            return ConfigLoader.getBoolean("RETRY_ENABLED", true);
        }

        /**
         * Number of EXTRA attempts per failed scenario. 1 means "run it once more",
         * 2 means "up to two more times". 0 disables retrying entirely.
         */
        public static int maxAttempts() {
            int value = ConfigLoader.getInt("RETRY_MAX_ATTEMPTS", 1);
            return Math.max(0, value);
        }

        /**
         * Scenarios carrying this tag are never retried. Useful for deliberately
         * negative tests and for checks whose first failure is already meaningful.
         */
        public static String excludeTag() {
            return ConfigLoader.getString("RETRY_TAG_EXCLUDE", "@no-retry");
        }

        /** Directory holding the retry ledger and the generated retry report. */
        public static String reportDir() {
            return ConfigLoader.getString("RETRY_REPORT_DIR", "target/retry");
        }

        /** Convenience: are retries actually in play for this run? */
        public static boolean active() {
            return enabled() && maxAttempts() > 0;
        }
    }

    /**
     * Retry analyser for Cucumber-JVM scenarios.
     *
     * WHY THIS EXISTS
     * ---------------
     * Surefire's built-in &lt;rerunFailingTestsCount&gt; does NOT work for Cucumber
     * scenarios: it understands JUnit test METHODS, whereas Cucumber reports one
     * JUnit Platform test per scenario through its own engine. Enabling it simply
     * has no effect. This class implements the equivalent for Cucumber properly.
     *
     * HOW IT WORKS
     * ------------
     * The analyser keeps a small JSON "ledger" (target/retry/retry-ledger.json)
     * that survives across Maven invocations. Each entry records, per scenario key,
     * how many times it has already been attempted and its latest outcome.
     *
     * 1. Run 1 - the suite runs. Every failed scenario is recorded and, if it has
     * attempts left, written to a rerun file plus the ledger.
     * 2. The runner re-invokes the suite selecting exactly those scenarios.
     * 3. Repeat until no scenario has attempts left (or it passes).
     *
     * The ledger is what makes the analyser terminate: a scenario that fails every
     * time stops being re-run once its attempts are exhausted, and is then reported
     * as a genuine failure rather than quietly passing.
     *
     * FLAKY vs FAILED
     * ---------------
     * A scenario that fails on attempt 1 and passes on attempt 2 is FLAKY, not
     * passing. The analyser tracks that distinction and reports it, so retries
     * cannot hide a real defect. See {@link #flakyScenarios()}.
     *
     * The ledger is always cleared at the start of a fresh (non-rerun) run, so a
     * stale file can never suppress a failure in a later CI build.
     */
    public static final class Analyzer {

        private static final Logger log = LoggerFactory.getLogger(Analyzer.class);
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

        /** Set by the runner on the command line to mark "this is a retry pass". */
        public static final String RERUN_FLAG = "retry.rerun";

        private static final Map<String, Entry> LEDGER = new ConcurrentHashMap<>();
        private static volatile boolean loaded = false;

        private Analyzer() {
        }

        /** One scenario's history across attempts. */
        public static class Entry {
            public int attempts;
            public boolean lastFailed;
            public List<String> attemptOutcomes = new ArrayList<>();
            public boolean everFailed;

            public Entry() {
            }
        }

        private static Path ledgerPath() {
            return Paths.get(Config.reportDir(), "retry-ledger.json");
        }

        private static Path rerunFile() {
            return Paths.get(Config.reportDir(), "rerun.txt");
        }

        private static synchronized void ensureLoaded() {
            if (loaded) {
                return;
            }
            loaded = true;
            Path path = ledgerPath();
            if (!Files.exists(path)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Map<String, Entry> parsed = GSON.fromJson(reader,
                        new TypeToken<LinkedHashMap<String, Entry>>() {
                        }.getType());
                if (parsed != null) {
                    LEDGER.putAll(parsed);
                }
                log.debug("Retry ledger loaded: {} entries", LEDGER.size());
            } catch (Exception e) {
                log.warn("Could not read retry ledger ({}), starting fresh: {}",
                        path, e.getMessage());
            }
        }

        /** True when this JVM was launched by the runner as a retry pass. */
        public static boolean isRetryPass() {
            return Boolean.getBoolean(RERUN_FLAG);
        }

        /**
         * Clears the ledger at the start of a fresh run so a previous build's state
         * cannot leak in. Called by the runner before the first suite execution.
         */
        public static synchronized void startFreshRun() {
            LEDGER.clear();
            loaded = true;
            try {
                Files.deleteIfExists(ledgerPath());
                Files.deleteIfExists(rerunFile());
                Files.deleteIfExists(Paths.get(Config.reportDir(), "retry-report.txt"));
            } catch (IOException e) {
                log.warn("Could not clear previous retry state: {}", e.getMessage());
            }
            log.debug("Retry ledger reset for a fresh run");
        }

        /**
         * Records the outcome of one scenario attempt.
         *
         * @param scenarioKey stable identity of the scenario (URI + line)
         * @param failed      whether this attempt failed
         * @param excluded    whether the scenario is tagged @no-retry
         * @return true when this failed scenario should be retried again
         */
        public static synchronized boolean record(String scenarioKey,
                boolean failed,
                boolean excluded) {
            ensureLoaded();
            Entry entry = LEDGER.computeIfAbsent(scenarioKey, k -> new Entry());
            entry.attempts++;
            entry.lastFailed = failed;
            entry.everFailed = entry.everFailed || failed;
            entry.attemptOutcomes.add(failed ? "FAILED" : "PASSED");

            if (!failed) {
                if (entry.everFailed) {
                    log.warn("FLAKY  {} passed on attempt {} after failing earlier: {}",
                            scenarioKey, entry.attempts, entry.attemptOutcomes);
                } else {
                    log.debug("PASSED {} (attempt {})", scenarioKey, entry.attempts);
                }
                return false;
            }

            if (!Config.active() || excluded) {
                log.error("FAILED {} on attempt {} (retries {} )",
                        scenarioKey, entry.attempts,
                        excluded ? "excluded" : "disabled");
                return false;
            }

            boolean hasAttemptsLeft = entry.attempts <= Config.maxAttempts();
            if (!hasAttemptsLeft) {
                log.error("FAILED {} after {} attempt(s) - retries exhausted",
                        scenarioKey, entry.attempts);
                return false;
            }

            log.warn("RETRY  {} failed on attempt {} of {}",
                    scenarioKey, entry.attempts, Config.maxAttempts() + 1);
            return true;
        }

        /** True when the given tag list marks the scenario as non-retryable. */
        public static boolean isExcluded(Iterable<String> tags) {
            if (tags == null) {
                return false;
            }
            String exclude = Config.excludeTag();
            for (String tag : tags) {
                if (tag != null && tag.equalsIgnoreCase(exclude)) {
                    return true;
                }
            }
            return false;
        }

        /** Scenarios that should be re-run in the next pass, in the order recorded. */
        public static synchronized Set<String> scenariosToRerun() {
            ensureLoaded();
            Set<String> rerun = new LinkedHashSet<>();
            for (Map.Entry<String, Entry> e : LEDGER.entrySet()) {
                Entry entry = e.getValue();
                if (entry.lastFailed
                        && Config.active()
                        && entry.attempts <= Config.maxAttempts()) {
                    rerun.add(e.getKey());
                }
            }
            return rerun;
        }

        /** Scenarios that failed on their first attempt but passed later. */
        public static synchronized List<String> flakyScenarios() {
            ensureLoaded();
            List<String> flaky = new ArrayList<>();
            for (Map.Entry<String, Entry> e : LEDGER.entrySet()) {
                Entry entry = e.getValue();
                if (entry.everFailed && !entry.lastFailed && entry.attempts > 1) {
                    flaky.add(e.getKey());
                }
            }
            Collections.sort(flaky);
            return flaky;
        }

        /** Scenarios still failing once every attempt has been used up. */
        public static synchronized List<String> stillFailing() {
            ensureLoaded();
            List<String> failed = new ArrayList<>();
            for (Map.Entry<String, Entry> e : LEDGER.entrySet()) {
                Entry entry = e.getValue();
                if (entry.lastFailed && entry.attempts > Config.maxAttempts()) {
                    failed.add(e.getKey());
                }
            }
            Collections.sort(failed);
            return failed;
        }

        /** Persists the ledger, the Cucumber rerun file and a human report. */
        public static synchronized void flush() {
            ensureLoaded();
            try {
                Path dir = Paths.get(Config.reportDir());
                Files.createDirectories(dir);

                try (Writer writer = Files.newBufferedWriter(ledgerPath(), StandardCharsets.UTF_8)) {
                    GSON.toJson(LEDGER, writer);
                }

                Set<String> rerun = scenariosToRerun();
                Files.write(rerunFile(), String.join(System.lineSeparator(), rerun)
                        .getBytes(StandardCharsets.UTF_8));

                List<String> report = new ArrayList<>();
                report.add("Retry analyser report");
                report.add("=====================");
                report.add("Retry enabled     : " + Config.active());
                report.add("Max extra attempts: " + Config.maxAttempts());
                report.add("");
                report.add("Scenarios recorded: " + LEDGER.size());
                report.add("Flaky (passed on a later attempt): " + flakyScenarios().size());
                for (String s : flakyScenarios()) {
                    report.add("    FLAKY  " + s);
                }
                report.add("Still failing after all attempts: " + stillFailing().size());
                for (String s : stillFailing()) {
                    report.add("    FAILED " + s);
                }
                report.add("Queued for rerun: " + rerun.size());
                for (String s : rerun) {
                    report.add("    RERUN  " + s);
                }
                Files.write(Paths.get(Config.reportDir(), "retry-report.txt"),
                        report, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("Could not write retry artefacts: {}", e.getMessage());
            }
        }

        /** Logs a compact summary at the end of a pass. */
        public static synchronized void logSummary() {
            int flaky = flakyScenarios().size();
            int failing = stillFailing().size();
            int queued = scenariosToRerun().size();
            if (flaky > 0) {
                log.warn("Retry summary: {} flaky, {} still failing, {} queued for rerun",
                        flaky, failing, queued);
                flakyScenarios().forEach(s -> log.warn("  FLAKY: {}", s));
            } else {
                log.info("Retry summary: no flaky scenarios, {} still failing, {} queued for rerun",
                        failing, queued);
            }
        }
    }
}
