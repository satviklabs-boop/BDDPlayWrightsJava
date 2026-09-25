package com.satviklabs.customLocators;

import java.util.Map;

/**
 * Locators for the login page, fed by {@code reads_CustomLogin.csv}.
 *
 * <p>
 * The static block runs once, when this class is first referenced, and reads
 * the whole CSV into {@link #locators}. Every selector fetched by {@link By}
 * then
 * delegates to {@link LoadLocators#get(Map, String)} - no per-field file
 * access.
 */
public final class ByLoginLocators {

    /** Whole Login.csv as key -&gt; selector. Loaded once by the static block. */
    public static final Map<String, String> locators;

    static {
        locators = LoadLocators.ctors(LoadLocators.Element.LOGIN);
    }

    /** Selector lookup into {@link #locators}. */
    public static class By extends LoadLocators {

        public static final String USERNAME_FIELD = get(locators, "usernameField");
        public static final String PASSWORD_FIELD = get(locators, "passwordField");
        public static final String LOGIN_BUTTON = get(locators, "loginButton");
        public static final String FLASH_MESSAGE = get(locators, "flashMessage");
        public static final String LOGOUT_BUTTON = get(locators, "logoutButton");
        public static final String HEADING = get(locators, "heading");
    }

    private ByLoginLocators() {
    }
}