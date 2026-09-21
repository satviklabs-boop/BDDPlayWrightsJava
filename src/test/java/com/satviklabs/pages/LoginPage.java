package com.satviklabs.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.satviklabs.config.ConfigLoader;

/**
 * Page object for https://the-internet.herokuapp.com/login
 *
 * All selectors are private and confined to this class.
 */
public class LoginPage extends BasePage {

    private static final String LOGIN_PATH = "/login";

    private final Locator usernameField;
    private final Locator passwordField;
    private final Locator loginButton;
    private final Locator flashMessage;
    private final Locator logoutButton;
    private final Locator heading;

    public LoginPage(Page page) {
        super(page);
        this.usernameField = page.locator("#username");
        this.passwordField = page.locator("#password");
        this.loginButton = page.locator("button[type='submit']");
        this.flashMessage = page.locator("#flash");
        this.logoutButton = page.locator("a[href='/logout']");
        this.heading = page.locator("h2");
    }

    /** Opens the login page and waits for it to be interactive. */
    public LoginPage open() {
        gotoPath(LOGIN_PATH);
        waitUntilVisible(usernameField);
        return this;
    }

    public LoginPage enterUsername(String username) {
        usernameField.fill(username);
        return this;
    }

    public LoginPage enterPassword(String password) {
        passwordField.fill(password);
        return this;
    }

    public LoginPage submit() {
        loginButton.click();
        page.waitForLoadState();
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
        return textOf(flashMessage);
    }

    public boolean isLoginFormVisible() {
        return isVisible(usernameField) && isVisible(passwordField) && isVisible(loginButton);
    }

    public boolean isLoggedIn() {
        return isVisible(logoutButton);
    }

    public String headingText() {
        return textOf(heading);
    }
}
