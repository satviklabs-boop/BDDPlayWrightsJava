package com.satviklabs.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.satviklabs.commonUtils.ConfigLoader;
import com.satviklabs.routine.GenericFunctions;

import java.util.Map;

/**
 * Page object for https://the-internet.herokuapp.com/login
 *
 * Selectors come from {@code locators/login.csv}, loaded once by the static
 * block below. The page holds the resulting key/selector map and creates
 * locators on demand, so there is no list of fields to keep in step: adding a
 * selector to the CSV and using {@code locators.get("newKey")} is enough.
 *
 * <p>Playwright {@link Locator} objects are lazy handles, so creating one per
 * call costs nothing measurable and is the idiomatic usage.
 */
public class LoginPage extends BasePage {

    private static final String LOGIN_PATH = "/login";

    /**
     * Loads {@code locators/login.csv} once, on first use of this class. Must be
     * declared before {@link #LOCATORS} below - static initializers run in
     * textual order.
     */
    static {
        GenericFunctions.loadLocators("login");
    }

    /** This page's key -> selector map, loaded by the block above. */
    private static final Map<String, String> LOCATORS =
            GenericFunctions.configureLocators("locators/login.csv");

    public LoginPage(Page page) {
        super(page);
    }

    /**
     * Creates a locator from this page's own selector set.
     *
     * <p>Routes through {@link GenericFunctions#get(Map, String)} rather than a
     * raw {@code Map.get}, so a mistyped key fails immediately with the list of
     * available keys instead of producing a null selector and a 90-second
     * Playwright timeout with no explanation.
     */
    private Locator locator(String key) {
        return page.locator(GenericFunctions.get(LOCATORS, key));
    }

    /** Opens the login page and waits for it to be interactive. */
    public LoginPage open() {
        gotoPath(LOGIN_PATH);
        waitUntilVisible(locator("usernameField"));
        return this;
    }

    public LoginPage enterUsername(String username) {
        locator("usernameField").fill(username);
        return this;
    }

    public LoginPage enterPassword(String password) {
        locator("passwordField").fill(password);
        return this;
    }

    public LoginPage submit() {
        locator("loginButton").click();
        // Wait for the DOM rather than the default "load" event; the latter
        // depends on third-party subresources and is intermittently slow on
        // the public demo host.
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return this;
    }

    /** Convenience: fill both fields and submit. */
    public LoginPage login(String username, String password) {
        enterUsername(username);
        enterPassword(password);
        return submit();
    }

    /** Convenience: log in with the credentials from configuration. */
    public LoginPage loginWithValidCredentials() {
        return login(ConfigLoader.uiUsername(), ConfigLoader.uiPassword());
    }

    public String flashMessageText() {
        return textOf(locator("flashMessage"));
    }

    public boolean isLoginFormVisible() {
        return isVisible(locator("usernameField"))
                && isVisible(locator("passwordField"))
                && isVisible(locator("loginButton"));
    }

    public boolean isLoggedIn() {
        return isVisible(locator("logoutButton"));
    }

    public String headingText() {
        return textOf(locator("heading"));
    }
}
