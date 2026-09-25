package com.satviklabs.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import com.satviklabs.baseClasses.ConfigLoader;
import com.satviklabs.pages.LoginPage;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single home for everything the test run needs to obtain live infrastructure:
 * the shared Playwright driver, a browser page, and an API client.
 *
 * The three providers were separate top-level classes before; grouping them
 * here
 * keeps the "how do I get a browser / an HTTP client" question answerable in
 * one
 * place. Each remains a self-contained nested type:
 *
 * Providers.PlaywrightProvider - the one driver for the whole run
 * Providers.Hooks - Cucumber hooks injecting per-scenario state
 * Providers.ApiClient - thin HTTP wrapper for API-only scenarios
 */
public final class Providers {

    private Providers() {
    }

    /**
     * Holds the single Playwright driver instance for the whole test run.
     *
     * Playwright's Java driver is an expensive process to start and is safe to
     * share across threads (each scenario gets its own Browser/Context/Page).
     *
     * It is deliberately NOT a ThreadLocal: a ThreadLocal value cannot be reliably
     * closed from a suite-level teardown, because that hook runs on a different
     * thread and would close it while other threads still need it.
     */
    public static final class PlaywrightProvider {

        private static final AtomicReference<Playwright> INSTANCE = new AtomicReference<>();

        private PlaywrightProvider() {
        }

        public static Playwright get() {
            Playwright existing = INSTANCE.get();
            if (existing != null) {
                return existing;
            }
            synchronized (PlaywrightProvider.class) {
                Playwright current = INSTANCE.get();
                if (current == null) {
                    current = Playwright.create();
                    INSTANCE.set(current);
                }
                return current;
            }
        }

        /** Closes the driver once, at the very end of the run. Idempotent. */
        public static synchronized void close() {
            Playwright instance = INSTANCE.getAndSet(null);
            if (instance != null) {
                try {
                    instance.close();
                } catch (Exception ignored) {
                    // The driver is being shut down anyway; nothing useful to do.
                }
            }
        }
    }

    /**
     * Cucumber hooks providing dependency injection of the "world" state.
     *
     * Browser and page are per-thread (one scenario = one thread when running in
     * parallel). The underlying Playwright driver is shared via
     * {@link PlaywrightProvider} and closed once at the end of the run, which also
     * keeps API-only scenarios free of browser cost.
     */
    public static class Hooks {

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

    /**
     * Thin HTTP wrapper around Playwright's APIRequestContext.
     *
     * It performs real HTTP calls with no browser overhead, sends the API key
     * header on every request, and exposes the raw status/body so step definitions
     * can assert on them.
     */
    public static class ApiClient {

        private final APIRequestContext context;

        public ApiClient(Playwright playwright) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Accept", "application/json");
            String apiKey = ConfigLoader.apiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                headers.put("x-api-key", apiKey);
            }

            this.context = playwright.request().newContext(
                    new APIRequest.NewContextOptions()
                            .setBaseURL(ConfigLoader.apiBaseUrl())
                            .setExtraHTTPHeaders(headers)
                            // Generous per-request budget: a slow public demo API over a
                            // shared runner should not look like a defect.
                            .setTimeout(ConfigLoader.apiTimeoutMs()));
        }

        // ------------------------------------------------------------- verbs

        public APIResponse get(String path) {
            return context.get(path);
        }

        public APIResponse post(String path, Object payload) {
            return context.post(path, RequestOptions.create().setData(payload));
        }

        public APIResponse put(String path, Object payload) {
            return context.put(path, RequestOptions.create().setData(payload));
        }

        public APIResponse patch(String path, Object payload) {
            return context.patch(path, RequestOptions.create().setData(payload));
        }

        public APIResponse delete(String path) {
            return context.delete(path);
        }

        // ------------------------------------------------------------ helpers

        /**
         * Parses the response body as a JSON object.
         *
         * Throws if the body is not valid JSON, or if the root is a JSON array -
         * use {@link #bodyAsJsonElement} when the response may be either shape.
         */
        public JsonObject bodyAsJson(APIResponse response) {
            return bodyAsJsonElement(response).getAsJsonObject();
        }

        /**
         * Parses the response body into any JSON value: object, array, or primitive.
         *
         * Some APIs return a bare array at the root ({@code [{...},{...}]} rather than
         * {@code {"data":[...]}}), so callers that only need to inspect the payload
         * should use this instead of {@link #bodyAsJson}.
         */
        public JsonElement bodyAsJsonElement(APIResponse response) {
            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new IllegalStateException(
                        "Response body was empty (HTTP " + response.status() + ")");
            }
            try {
                return JsonParser.parseString(text);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Response body was not valid JSON (HTTP " + response.status()
                                + "): " + truncate(text),
                        e);
            }
        }

        private String truncate(String text) {
            return text.length() <= 500 ? text : text.substring(0, 500) + "...";
        }

        public String bodyAsText(APIResponse response) {
            return response.text();
        }

        /** Case-insensitive header lookup, since servers vary in casing. */
        public String header(APIResponse response, String name) {
            return response.headers().get(name.toLowerCase());
        }

        public void dispose() {
            context.dispose();
        }
    }
}
