package com.satviklabs.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.satviklabs.config.ConfigLoader;
import com.satviklabs.locators.Locators;

/**
 * Page object for https://the-internet.herokuapp.com/login
 *
 * Selectors come from {@link Locators.Login} so that this class stays free of
 * raw CSS strings and every page has a single source of truth for its DOM.
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
        this.usernameField = page.locator(Locators.Login.USERNAME_FIELD);
        this.passwordField = page.locator(Locators.Login.PASSWORD_FIELD);
        this.loginButton = page.locator(Locators.Login.LOGIN_BUTTON);
        this.flashMessage = page.locator(Locators.Login.FLASH_MESSAGE);
        this.logoutButton = page.locator(Locators.Login.LOGOUT_BUTTON);
        this.heading = page.locator(Locators.Login.HEADING);
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
