package com.satviklabs.support.retry;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JUnit Platform listener that feeds every scenario outcome into the
 * {@link RetryAnalyzer}.
 *
 * It listens at the TEST level only (Cucumber emits one TEST descriptor per
 * scenario) and ignores the engine/suite descriptors, so the ledger counts
 * scenarios rather than containers.
 *
 * Registered automatically through the JUnit Platform ServiceLoader mechanism
 * (src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener),
 * which means it applies to the whole suite with no change to the runner class.
 */
public class RetryListener implements TestExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(RetryListener.class);

    /** Scenario keys seen in this pass, so a rerun file lists each once. */
    private final Set<String> seenKeys = ConcurrentHashMap.newKeySet();

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        log.debug("Retry listener attached (retry pass = {}, retry active = {})",
                RetryAnalyzer.isRetryPass(), RetryConfig.active());
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier,
                                  TestExecutionResult testExecutionResult) {
        if (!testIdentifier.isTest()) {
            return;
        }
        String key = scenarioKey(testIdentifier);
        if (!seenKeys.add(key)) {
            return;
        }
        boolean failed = testExecutionResult.getStatus() == TestExecutionResult.Status.FAILED;
        RetryAnalyzer.record(key, failed, isExcluded(testIdentifier));
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        RetryAnalyzer.flush();
        RetryAnalyzer.logSummary();
    }

    /**
     * Stable, re-runnable identity for a scenario.
     *
     * Cucumber's unique id already contains the feature URI and the scenario
     * line (e.g. [engine:cucumber]/[feature:.../login.feature]/[scenario:12]),
     * which is exactly what the retry pass needs to select it again. The leading
     * engine segment is dropped so the key stays readable in the ledger.
     */
    private static String scenarioKey(TestIdentifier testIdentifier) {
        String uniqueId = testIdentifier.getUniqueId();
        int marker = uniqueId.indexOf("[feature:");
        return marker >= 0 ? uniqueId.substring(marker) : uniqueId;
    }

    /**
     * Reads the scenario's tags from the JUnit Platform tag set, which Cucumber
     * populates from the Gherkin tags.
     */
    private static boolean isExcluded(TestIdentifier testIdentifier) {
        Set<String> tags = testIdentifier.getTags().stream()
                .map(tag -> tag.getName())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return RetryAnalyzer.isExcluded(tags);
    }
}
