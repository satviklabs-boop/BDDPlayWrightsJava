package com.satviklabs.core;

import com.satviklabs.baseClasses.ConfigLoader;
import io.cucumber.testng.PickleWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The scenario-retry mechanism for the TestNG-based suite.
 *
 * <h2>Why this exists</h2>
 * Surefire's built-in {@code rerunFailingTestsCount} does NOT work for Cucumber:
 * it understands JUnit test METHODS, whereas Cucumber reports one test per
 * scenario through its own engine. This was true on the old JUnit Platform setup
 * and remains true, which is why retry is handled here instead.
 *
 * <h2>What it does</h2>
 * {@link Analysis} decides whether a failed scenario should be re-run, and
 * {@link Reporter} keeps the bookkeeping that lets us distinguish a scenario that
 * <em>passed on a retry</em> from one that genuinely passed. That distinction
 * matters: without it, retrying silently converts real defects into green
 * builds, and a flaky suite becomes indistinguishable from a healthy one.
 *
 * <p>Unlike the previous file-backed ledger, this is entirely in-process. TestNG
 * owns the retry loop, so there is no cross-JVM state to persist and no rerun
 * file to keep in sync.
 */
public final class Retry {

    private Retry() {
    }

    /**
     * Configuration for the retry mechanism.
     *
     * <p>Every value resolves through {@link ConfigLoader}, so it can be
     * overridden on the command line (-DRETRY_MAX_ATTEMPTS=2), in the
     * environment, or in .env / config.properties exactly like every other
     * setting.
     *
     * <p>RETRY_ENABLED (default true) master on/off switch
     * <p>RETRY_MAX_ATTEMPTS (default 1) extra runs per failed scenario
     * <p>RETRY_TAG_EXCLUDE (default @no-retry) scenarios that must never retry
     */
    public static final class Config {

        private Config() {
        }

        /** Whether failed scenarios may be retried at all. */
        public static boolean enabled() {
            return ConfigLoader.getBoolean("RETRY_ENABLED", true);
        }

        /**
         * Number of EXTRA attempts per failed scenario. 1 means "run it once
         * more", 2 means "up to two more times". 0 disables retrying entirely.
         */
        public static int maxAttempts() {
            int value = ConfigLoader.getInt("RETRY_MAX_ATTEMPTS", 1);
            return Math.max(0, value);
        }

        /**
         * Scenarios carrying this tag are never retried. Useful for
         * deliberately negative tests and for checks whose first failure is
         * already meaningful.
         */
        public static String excludeTag() {
            return ConfigLoader.getString("RETRY_TAG_EXCLUDE", "@no-retry");
        }

        /** Convenience: are retries actually in play for this run? */
        public static boolean active() {
            return enabled() && maxAttempts() > 0;
        }
    }

    /**
     * One scenario's history across its attempts.
     *
     * <p>{@code excluded} is captured on the first attempt and then frozen:
     * the tag list is read from the pickle, and on a retry TestNG hands the
     * analyser a fresh result whose parameters are still available, but
     * re-reading it per attempt would let a scenario's identity drift.
     */
    public static final class History {
        public final Set<String> tags;
        public final String uri;
        public final int line;
        public int attempts;
        public final List<String> outcomes = new ArrayList<>();
        public boolean everFailed;

        History(String key, String uri, int line, Set<String> tags) {
            this.uri = uri;
            this.line = line;
            this.tags = tags;
        }

        /** True when this scenario failed but later passed: flaky, not healthy. */
        public boolean isFlaky() {
            return everFailed && outcomes.contains("PASSED") && attempts > 1;
        }
    }

    /**
     * Records attempt outcomes so a flaky scenario can be told apart from a
     * genuinely passing one.
     *
     * <p>Keyed by URI plus line rather than scenario name, because two scenarios
     * in different features can share a name and must not share a history.
     */
    public static final class Reporter {

        private static final Map<String, History> HISTORIES = new ConcurrentHashMap<>();
        private static final Logger log = LoggerFactory.getLogger(Reporter.class);

        private Reporter() {
        }

        static String key(String uri, int line) {
            return uri + ":" + line;
        }

        /** The history for a scenario, created on first sight. */
        static synchronized History history(String uri, int line, Set<String> tags) {
            return HISTORIES.computeIfAbsent(key(uri, line), k -> new History(k, uri, line, tags));
        }

        public static void recordOutcome(String uri, int line, Set<String> tags, boolean failed) {
            History history = history(uri, line, tags);
            synchronized (history) {
                history.attempts++;
                history.everFailed |= failed;
                history.outcomes.add(failed ? "FAILED" : "PASSED");
            }
        }

        /** Scenarios that failed at least once but passed on a later attempt. */
        public static synchronized List<String> flakyScenarios() {
            List<String> flaky = new ArrayList<>();
            for (Map.Entry<String, History> e : HISTORIES.entrySet()) {
                History h = e.getValue();
                if (h.isFlaky()) {
                    flaky.add(e.getKey());
                }
            }
            Collections.sort(flaky);
            return flaky;
        }

        public static synchronized int scenarioCount() {
            return HISTORIES.size();
        }

        /** Clears state at the start of a fresh run. */
        public static synchronized void reset() {
            HISTORIES.clear();
        }

        /** Logs what the run proved about suite health. */
        public static synchronized void logSummary() {
            List<String> flaky = flakyScenarios();
            if (flaky.isEmpty()) {
                log.info("Retry summary: {} scenario(s) run, no flaky scenarios",
                        HISTORIES.size());
                return;
            }
            log.warn("Retry summary: {} of {} scenario(s) passed only after a retry "
                            + "- these are FLAKY, not healthy",
                    flaky.size(), HISTORIES.size());
            flaky.forEach(s -> log.warn("  FLAKY: {}", s));
        }
    }

    /**
     * The {@link IRetryAnalyzer} TestNG calls after each failed scenario.
     *
     * <p>Referenced from the runner's {@code @Test(retryAnalyzer = ...)}. TestNG
     * instantiates it once per class and asks it, for each failure, whether to
     * try again.
     */
    public static final class Analysis implements IRetryAnalyzer {

        private final Map<String, Integer> retriesSoFar = new ConcurrentHashMap<>();

        @Override
        public boolean retry(ITestResult result) {
            String uri = uriOf(result);
            int line = lineOf(result);
            Set<String> tags = tagsOf(result);
            String key = Reporter.key(uri, line);

            Reporter.recordOutcome(uri, line, tags, result.getStatus() == ITestResult.FAILURE);

            if (!Config.active()) {
                log.debug("Retry disabled; not retrying {}", key);
                return false;
            }
            if (isExcluded(tags)) {
                log.info("Scenario {} is tagged {} - never retried", key, Config.excludeTag());
                return false;
            }

            int used = retriesSoFar.merge(key, 1, Integer::sum);
            if (used > Config.maxAttempts()) {
                log.error("FAILED {} after {} attempt(s) - retries exhausted", key, used + 1);
                return false;
            }
            log.warn("RETRY  {} failed; attempt {} of {}",
                    key, used + 1, Config.maxAttempts() + 1);
            return true;
        }

        private static boolean isExcluded(Set<String> tags) {
            String exclude = Config.excludeTag();
            for (String tag : tags) {
                if (tag != null && tag.equalsIgnoreCase(exclude)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * The pickle is the second DataProvider argument of the base class's
         * runScenario method, which is what gives us the scenario's tags. If it
         * is missing we fall back to an empty set rather than guessing, so the
         * scenario is simply not excluded.
         */
        private static Set<String> tagsOf(ITestResult result) {
            for (Object parameter : result.getParameters()) {
                if (parameter instanceof PickleWrapper wrapper
                        && wrapper.getPickle() != null) {
                    return new LinkedHashSet<>(wrapper.getPickle().getTags());
                }
            }
            return Collections.emptySet();
        }

        private static String uriOf(ITestResult result) {
            for (Object parameter : result.getParameters()) {
                if (parameter instanceof PickleWrapper wrapper
                        && wrapper.getPickle() != null) {
                    return String.valueOf(wrapper.getPickle().getUri());
                }
            }
            return result.getName();
        }

        private static int lineOf(ITestResult result) {
            for (Object parameter : result.getParameters()) {
                if (parameter instanceof PickleWrapper wrapper
                        && wrapper.getPickle() != null) {
                    return wrapper.getPickle().getScenarioLine();
                }
            }
            return 0;
        }

        private static final Logger log = LoggerFactory.getLogger(Analysis.class);
    }
}
