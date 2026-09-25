package com.satviklabs.customLocators;

import com.satviklabs.routine.GenericFunctions;

/**
 * Locators for the login page, fed by {@code locators/login.csv}.
 *
 * <p>
 * The static block reads the CSV once, on first use of this class, and binds
 * it to this class. Every constant below then resolves against this page's own
 * map, so no lookup can reach another page's file.
 */
public final class LoginLocators {

    /**
     * Must come first: static initializers run in textual order, so a block
     * placed after the fields would run too late and the lookups below would
     * find no loaded map.
     */
    static {
        GenericFunctions.loadLocators("login");
    }

    public static final String USERNAME_FIELD = GenericFunctions.get("usernameField");
    public static final String PASSWORD_FIELD = GenericFunctions.get("passwordField");
    public static final String LOGIN_BUTTON = GenericFunctions.get("loginButton");
    public static final String FLASH_MESSAGE = GenericFunctions.get("flashMessage");
    public static final String LOGOUT_BUTTON = GenericFunctions.get("logoutButton");
    public static final String HEADING = GenericFunctions.get("heading");

    private LoginLocators() {
    }
}