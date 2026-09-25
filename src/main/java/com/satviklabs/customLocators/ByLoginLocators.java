package com.satviklabs.customLocators;

/**
 * Locators for the login page, fed by {@code reads_CustomLogin.csv}.
 *
 * <p>The static block reads the CSV once, on first use of this class. Every
 * selector below then resolves against this page's own map, so no lookup can
 * reach another page's file.
 */
public final class ByLoginLocators extends LoadLocators {

    static {
        loadLocators("Login");
    }

    /** Selector lookup into this page's CSV. */
    public static class By extends LoadLocators {

        /**
         * Must come first: static initializers run in textual order, so a block
         * placed after the fields would run too late and the lookups below would
         * find no loaded map. Loading here keeps {@code By} self-sufficient - a
         * nested class does not trigger its enclosing class's static block.
         */
        static {
            loadLocators("Login");
        }

        public static final String USERNAME_FIELD = get("usernameField");
        public static final String PASSWORD_FIELD = get("passwordField");
        public static final String LOGIN_BUTTON = get("loginButton");
        public static final String FLASH_MESSAGE = get("flashMessage");
        public static final String LOGOUT_BUTTON = get("logoutButton");
        public static final String HEADING = get("heading");

        private By() {
        }
    }

    private ByLoginLocators() {
    }
}