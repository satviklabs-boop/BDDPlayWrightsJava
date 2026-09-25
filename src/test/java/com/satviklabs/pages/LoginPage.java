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
 * Selectors come from {@code locators/login.csv}, loaded once into
 * {@link #LOCATORS}. Locators are created on demand rather than held as fields,
 * so adding a selector to the CSV needs no change here.
 *
 * <p>Playwright {@link Locator} objects are lazy handles, so creating one per
 * call is the idiomatic usage and costs nothing measurable.
 */
public class LoginPage extends BasePage {

    private static final String LOGIN_PATH = "/login";

    /** This page's key -> selector map, read from the CSV once per JVM. */
    private static final Map<String, String> LOCATORS =
            GenericFunctions.loadLocators("login");

    public LoginPage(Page page) {
        super(page);
    }

    /**
     * Creates a locator from this page's own selector set.
     *
     * <p>Goes through {@link GenericFunctions#get(Map, String)} so a mistyped key
     * fails immediately with the list of available keys, rather than producing a
     * null selector and a long Playwright timeout with no explanation.
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
