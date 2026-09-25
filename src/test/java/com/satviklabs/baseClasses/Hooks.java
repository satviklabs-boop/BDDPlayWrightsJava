package com.satviklabs.baseClasses;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.satviklabs.pages.LoginPage;
import com.satviklabs.baseClasses.PlaywrightProvider;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cucumber hooks providing dependency injection of the "world" state.
 *
 * Browser and page are per-thread (one scenario = one thread when running in
 * parallel). The underlying Playwright driver is shared via
 * {@link PlaywrightProvider} and closed once at the end of the run, which also
 * keeps API-only scenarios free of browser cost.
 */
public class Hooks {

    private static final Logger log = LoggerFactory.getLogger(Hooks.class);

    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();
    private static final ThreadLocal<ApiClient> API_CLIENT = new ThreadLocal<>();

    /** The current scenario's page, launching a browser on first use. */
    public Page page() {
        if (PAGE.get() == null) {
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                    .setHeadless(ConfigLoader.headless())
                    .setSlowMo(ConfigLoader.slowMo());
            Browser browser = PlaywrightProvider.get().chromium().launch(options);
            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions().setViewportSize(1280, 800));
            context.setDefaultTimeout(ConfigLoader.defaultTimeoutMs());
            BROWSER.set(browser);
            CONTEXT.set(context);
            PAGE.set(context.newPage());
        }
        return PAGE.get();
    }

    /** The current scenario's login page object. */
    public LoginPage loginPage() {
        return new LoginPage(page());
    }

    /** The current scenario's API client, created on first use (no browser). */
    public ApiClient apiClient() {
        if (API_CLIENT.get() == null) {
            API_CLIENT.set(new ApiClient(PlaywrightProvider.get()));
        }
        return API_CLIENT.get();
    }

    @Before(order = 0)
    public void beforeEachScenario(Scenario scenario) {
        log.info(">>> Starting scenario: {}", scenario.getName());
    }

    @After(order = 0)
    public void afterEachScenario(Scenario scenario) {
        Page current = PAGE.get();
        if (scenario.isFailed() && current != null) {
            try {
                // Attaching the URL first means the ExtentReport shows where the
                // browser actually was when it broke, next to the screenshot.
                scenario.log("Page URL at failure: " + current.url());
            } catch (Exception e) {
                log.debug("Could not read the page URL: {}", e.getMessage());
            }
            try {
                LoginPage loginPage = new LoginPage(current);
                String path = loginPage.screenshot("FAILED-" + scenario.getName())
                        .toAbsolutePath().toString();
                log.error("Scenario failed. Screenshot saved to {}", path);
                // The "image/png" media type is what lets the Extent Reports
                // adapter embed this as a picture on the failing step rather
                // than a bare line of text. extent.properties sets
                // screenshot.mediatype=base64, so the image is inlined and
                // survives being downloaded as a CI artefact.
                scenario.attach(current.screenshot(), "image/png", "Failure screenshot");
            } catch (Exception e) {
                log.warn("Could not capture failure screenshot: {}", e.getMessage());
            }
        }

        // Teardown must never mask the real failure, hence the guarded blocks.
        disposeQuietly();
        log.info("<< Finished scenario: {} [{}]", scenario.getName(), scenario.getStatus());
    }

    private void disposeQuietly() {
        ApiClient api = API_CLIENT.get();
        if (api != null) {
            try {
                api.dispose();
            } catch (Exception e) {
                log.debug("API client dispose failed: {}", e.getMessage());
            }
            API_CLIENT.remove();
        }

        BrowserContext context = CONTEXT.get();
        if (context != null) {
            try {
                context.close();
            } catch (Exception e) {
                log.debug("Context close failed: {}", e.getMessage());
            }
            CONTEXT.remove();
        }

        Browser browser = BROWSER.get();
        if (browser != null) {
            try {
                browser.close();
            } catch (Exception e) {
                log.debug("Browser close failed: {}", e.getMessage());
            }
            BROWSER.remove();
        }

        PAGE.remove();
    }

    /** Closes the shared Playwright driver. Called once after the last scenario. */
    public static void shutdown() {
        PlaywrightProvider.close();
    }
}
