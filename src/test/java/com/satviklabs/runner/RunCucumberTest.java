package com.satviklabs.runner;

import com.satviklabs.baseClasses.Hooks;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.AfterSuite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PUBLISH_QUIET_PROPERTY_NAME;

/**
 * Single entry point for the whole suite.
 *
 * Run everything:       mvn test
 * Run by tag:           mvn test -Dcucumber.filter.tags="@smoke"
 * Run one feature file: use a tag that is unique to it (see the note below).
 *
 * Tag filters can also be passed via the Maven profiles in pom.xml
 * (-Psmoke, -Pregression, -Pui, -Papi).
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.satviklabs.stepDefinitions,com.satviklabs.hooks")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, "
        + "html:target/cucumber-report.html, "
        + "json:target/cucumber-report.json, "
        + "junit:target/TEST-cucumber.xml, "
        // Extent Reports: the adapter consumes the normal Gherkin events, so
        // there is no custom listener to maintain. The ":target/extent-report"
        // suffix is an output DIRECTORY and is mandatory - Cucumber aborts the
        // whole run with "You must supply an output argument" without it.
        // Styling (title, theme, screenshots) comes from extent.properties on
        // the test class path.
        + "com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter:target/extent-report")
@ConfigurationParameter(key = PLUGIN_PUBLISH_QUIET_PROPERTY_NAME, value = "true")
public class RunCucumberTest {

    /**
     * Cucumber-JVM with the JUnit Platform engine does not surface a suite-level
     * teardown hook, so Playwright is closed here once all scenarios are done.
     * Browsers are already closed per-scenario in {@code Hooks}.
     */
    @AfterSuite
    static void tearDownSuite() {
        Hooks.shutdown();
    }

    //
    // NOTE ON SELECTING FEATURES
    //
    // This suite discovers features from the classpath ("features"), so narrow a
    // run with TAGS rather than with the cucumber.features property:
    //
    //   mvn test -Dcucumber.filter.tags="@api-smoke"
    //
    // Setting cucumber.features at the same time makes Cucumber ignore all other
    // discovery selectors and the engine fails with
    // "TestEngine with ID 'cucumber' failed to discover tests".
    //
}
