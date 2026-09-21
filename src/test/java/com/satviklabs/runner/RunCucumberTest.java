package com.satviklabs.runner;

import com.satviklabs.hooks.Hooks;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.AfterSuite;

import static io.cucumber.junit.platform.engine.Constants.FEATURES_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PUBLISH_QUIET_PROPERTY_NAME;

/**
 * Single entry point for the whole suite.
 *
 * Run everything: mvn test
 * Run by tag: mvn test -Dcucumber.filter.tags="@smoke"
 * Run a single feature: mvn test
 * -Dcucumber.features=src/test/resources/features/ui/login.feature
 *
 * Tag filters can also be passed via the Maven profiles in pom.xml
 * (-Psmoke, -Pregression, -Pui, -Papi).
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.satviklabs.steps,com.satviklabs.hooks")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, "
        + "html:target/cucumber-report.html, "
        + "json:target/cucumber-report.json, "
        + "junit:target/cucumber-report.xml")
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

    static {
        // Allow the feature path to be overridden without editing this class.
        String features = System.getProperty("cucumber.features");
        if (features != null && !features.isBlank()) {
            System.setProperty(FEATURES_PROPERTY_NAME, features);
        }
    }
}
