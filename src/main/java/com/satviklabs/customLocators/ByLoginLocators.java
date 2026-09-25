package com.satviklabs.customLocators;

import java.util.Map;

/**
 * Locators for the login page, fed by {@code reads_CustomLogin.csv}.
 *
 * <p>{@link #LOCATORS} is read once, when this class is first used, and every
 * selector below resolves against it. Because the map is passed explicitly to
 * {@link LoadLocators#select(Map, String)}, this page can only ever read its own
 * CSV - it has no way to reach another page's selectors.
 */
public final class ByLoginLocators extends LoadLocators {

    /** {@code reads_CustomLogin.csv} as key -&gt; selector. Loaded once. */
    public static final Map<String, String> LOCATORS = ctors(Element.LOGIN);

    /** Selector lookup into {@link #LOCATORS}. */
    public static class By extends LoadLocators {

        public static final String USERNAME_FIELD = select(LOCATORS, "usernameField");
        public static final String PASSWORD_FIELD = select(LOCATORS, "passwordField");
        public static final String LOGIN_BUTTON = select(LOCATORS, "loginButton");
        public static final String FLASH_MESSAGE = select(LOCATORS, "flashMessage");
        public static final String LOGOUT_BUTTON = select(LOCATORS, "logoutButton");
        public static final String HEADING = select(LOCATORS, "heading");

        private By() {
        }
    }

    private ByLoginLocators() {
    }
}