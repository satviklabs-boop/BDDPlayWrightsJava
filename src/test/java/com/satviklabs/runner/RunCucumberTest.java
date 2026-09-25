package com.satviklabs.runner;

import com.satviklabs.core.Providers.Hooks;
import com.satviklabs.core.Retry.Analysis;
import com.satviklabs.core.Retry.Reporter;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import io.cucumber.testng.PickleWrapper;
import org.testng.ITestContext;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

/**
 * Single entry point for the whole suite, running on TestNG.
 *
 * <p>
 * Run everything: {@code mvn test}. Run by tag:
 * {@code mvn test -Dcucumber.filter.tags="@smoke"}.
 *
 * <h2>Why TestNG</h2>
 * The suite previously ran on the JUnit Platform, which cannot retry Cucumber
 * scenarios: Surefire's {@code rerunFailingTestsCount} keys off JUnit test
 * METHODS, whereas Cucumber reports one platform test per scenario through its
 * own engine, so the setting silently does nothing. TestNG invokes
 * {@link org.testng.IRetryAnalyzer} once per scenario, so a failure can
 * actually
 * be re-run in-process.
 *
 * <p>
 * The retry policy lives in {@link com.satviklabs.core.Retry.Analysis}, which
 * is what every scenario's {@code @Test(retryAnalyzer = ...)} points at.
 */
@CucumberOptions(features = "src/test/resources/features", glue = { "com.satviklabs.stepDefinitions",
        "com.satviklabs.core" }, plugin = {
                "pretty",
                "html:target/cucumber-report.html",
                "json:target/cucumber-report.json",
                // Extent Reports dashboard, driven by the official Cucumber 7
                // adapter. The trailing ":target/extent-report" is an output
                // DIRECTORY and is mandatory - Cucumber aborts the run with
                // "You must supply an output argument" without it.
                "com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter:target/extent-report"
        })
public class RunCucumberTest extends AbstractTestNGCucumberTests {

    /**
     * Runs one scenario, with retry attached.
     *
     * <p>This deliberately overrides the base class's {@code runScenario} so the
     * only {@code @Test} in the suite carries
     * {@code retryAnalyzer = Analysis.class}. The base class declares its own
     * {@code runScenario} without it, and an override replaces it, so the retry
     * policy applies to every scenario in one place.
     */
    @Override
    @org.testng.annotations.Test(
            groups = "cucumber",
            description = "Runs Cucumber Scenarios",
            dataProvider = "scenarios",
            retryAnalyzer = Analysis.class)
    public void runScenario(PickleWrapper pickleWrapper,
                            io.cucumber.testng.FeatureWrapper featureWrapper) {
        super.runScenario(pickleWrapper, featureWrapper);
    }

    @BeforeSuite(alwaysRun = true)
    public void resetRetryState() {
        Reporter.reset();
    }

    /**
     * Closes the shared Playwright driver once the whole suite is done.
     *
     * <p>
     * Browsers are already closed per-scenario by {@link Hooks}; this only
     * shuts down the driver process itself.
     */
    @AfterSuite(alwaysRun = true)
    public void tearDownSuite(ITestContext context) {
        Reporter.logSummary();
        Hooks.shutdown();
    }
}
