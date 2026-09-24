package com.satviklabs.locators;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Typed accessor layer over the externalized selector store.
 *
 * <p>Why one class instead of a file per page: the suite is expected to grow to
 * a dozen or more screens (login, accounts, customers, ...). Splitting selectors
 * across many small files scatters the DOM knowledge and makes it easy for two
 * page objects to drift onto slightly different selectors for the same element.
 * One {@code Locators} class keeps every selector in a single, greppable place.
 *
 * <p>Where the actual selector strings live: they are NOT hard-coded here any
 * more. They live in {@code src/test/resources/Locators.text} as plain
 * {@code PAGE.ELEMENT=selector} lines, for example:
 *
 * <pre>
 *   LOGIN.USERNAME_FIELD=#username
 * </pre>
 *
 * <p>This class only turns those keys into typed constants, so existing code
 * keeps working unchanged:
 *
 * <pre>
 *   page.locator(Locators.Login.USERNAME_FIELD)
 *   page.locator(Locators.Customers.SEARCH_BOX)
 * </pre>
 *
 * <p>Layout: one nested {@code static final class} per page/screen, each
 * grouping its selectors. Only page objects read these constants - step
 * definitions never touch them.
 *
 * <p>The file is read once, lazily, and cached. A missing key fails fast with a
 * clear message rather than returning an empty selector that would blow up much
 * later as a confusing Playwright timeout.
 *
 * <p>Note on the file name: it is {@code Locators.text}, not a standard
 * {@code .properties} file. {@code .text} is not a default Maven resource
 * extension, so {@code pom.xml} explicitly lists it under {@code <testResources>}
 * to keep it on the test classpath. The content is nevertheless a plain stream of
 * {@code KEY=value} lines, which {@link Properties#load(InputStream)} parses
 * happily regardless of the file extension.
 */
public final class Locators {

    private static final String LOCATORS_FILE = "Locators.text";

    private static final Properties PROPS = load();

    private Locators() {
    }

    /** Reads the locators file from the test classpath exactly once. */
    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = Locators.class.getClassLoader()
                .getResourceAsStream(LOCATORS_FILE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Locator file '" + LOCATORS_FILE
                                + "' not found on the test classpath. Expected it at "
                                + "src/test/resources/" + LOCATORS_FILE
                                + " and listed in <testResources> of pom.xml.");
            }
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Unable to read locator file '" + LOCATORS_FILE + "'", e);
        }
        return props;
    }

    /**
     * Returns the selector registered under {@code key}.
     *
     * @param key the {@code PAGE.ELEMENT} key, e.g. {@code "LOGIN.USERNAME_FIELD"}
     * @throws IllegalStateException when the key is absent, so a typo surfaces
     *                               immediately instead of as a later timeout
     */
    private static String get(String key) {
        String value = PROPS.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Missing locator '" + key + "' in " + LOCATORS_FILE);
        }
        return value.trim();
    }

    // =====================================================================
    // Login page - https://the-internet.herokuapp.com/login
    // =====================================================================
    public static final class Login {

        private Login() {
        }

        /** Username input on the login form. */
        public static final String USERNAME_FIELD = get("LOGIN.USERNAME_FIELD");

        /** Password input on the login form. */
        public static final String PASSWORD_FIELD = get("LOGIN.PASSWORD_FIELD");

        /** Submit button of the login form. */
        public static final String LOGIN_BUTTON = get("LOGIN.LOGIN_BUTTON");

        /** Flash banner showing the success or error message after submit. */
        public static final String FLASH_MESSAGE = get("LOGIN.FLASH_MESSAGE");

        /** Logout link, rendered only once the user is authenticated. */
        public static final String LOGOUT_BUTTON = get("LOGIN.LOGOUT_BUTTON");

        /** Page heading (h2) of the login screen. */
        public static final String HEADING = get("LOGIN.HEADING");
    }

    // =====================================================================
    // Accounts page
    //
    // Placeholder section. Fill in the real selectors once the screen exists;
    // keeping the empty section here documents the intended shape and stops
    // selectors from leaking back into page objects.
    // =====================================================================
    public static final class Accounts {

        private Accounts() {
        }

        /** Heading of the accounts list screen. */
        public static final String PAGE_HEADING = get("ACCOUNTS.PAGE_HEADING");

        /** "Create account" action button. */
        public static final String CREATE_BUTTON = get("ACCOUNTS.CREATE_BUTTON");

        /** Table listing the accounts. */
        public static final String ACCOUNTS_TABLE = get("ACCOUNTS.ACCOUNTS_TABLE");

        /** Row selector within the accounts table. */
        public static final String ACCOUNT_ROW = get("ACCOUNTS.ACCOUNT_ROW");
    }

    // =====================================================================
    // Customers page
    // =====================================================================
    public static final class Customers {

        private Customers() {
        }

        /** Heading of the customers list screen. */
        public static final String PAGE_HEADING = get("CUSTOMERS.PAGE_HEADING");

        /** Search input used to filter customers. */
        public static final String SEARCH_BOX = get("CUSTOMERS.SEARCH_BOX");

        /** "Add customer" action button. */
        public static final String ADD_BUTTON = get("CUSTOMERS.ADD_BUTTON");

        /** Row selector within the customers table. */
        public static final String CUSTOMER_ROW = get("CUSTOMERS.CUSTOMER_ROW");

        /** Per-row edit action. */
        public static final String EDIT_ROW_BUTTON = get("CUSTOMERS.EDIT_ROW_BUTTON");
    }
}