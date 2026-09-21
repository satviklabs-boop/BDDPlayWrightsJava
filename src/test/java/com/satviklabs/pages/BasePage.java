package com.satviklabs.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.satviklabs.config.ConfigLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Shared behaviour for every page object.
 *
 * Rule of the framework: locators live ONLY in page objects, never in step
 * definitions. Steps stay declarative and business-readable.
 */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    /** Navigates to a path relative to the configured UI base URL. */
    public void gotoPath(String path) {
        String normalised = path.startsWith("/") ? path : "/" + path;
        page.navigate(ConfigLoader.uiBaseUrl() + normalised);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    }

    public String currentUrl() {
        return page.url();
    }

    public String title() {
        return page.title();
    }

    /** Reads trimmed visible text, returning "" when the element is absent. */
    protected String textOf(Locator locator) {
        Locator first = locator.first();
        if (first.count() == 0) {
            return "";
        }
        try {
            return first.innerText().trim();
        } catch (Exception e) {
            return "";
        }
    }

    protected boolean isVisible(Locator locator) {
        Locator first = locator.first();
        return first.count() > 0 && first.isVisible();
    }

    protected void waitUntilVisible(Locator locator) {
        locator.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(ConfigLoader.defaultTimeoutMs()));
    }

    /** Saves a full-page screenshot for failure triage. Returns the path written. */
    public Path screenshot(String label) {
        try {
            Path dir = Paths.get(ConfigLoader.artifactDir());
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String safeLabel = label.replaceAll("[^a-zA-Z0-9-_]", "_");
            Path target = dir.resolve(safeLabel + "-" + stamp + ".png");
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(target)
                    .setFullPage(true));
            return target;
        } catch (Exception e) {
            throw new IllegalStateException("Could not capture screenshot", e);
        }
    }
}
