package com.satviklabs.locators;

/**
 * The single, centralized store for every selector in the framework.
 *
 * <p>Why one file instead of a file per page: the suite is expected to grow to
 * a dozen or more screens (login, accounts, customers, ...). Splitting selectors
 * across many small files scatters the DOM knowledge and makes it easy for two
 * page objects to drift onto slightly different selectors for the same element.
 * One {@code Locators} class keeps every selector in a single, greppable place.
 *
 * <p>Layout: one nested {@code static final class} per page/screen, each
 * grouping its selectors. Only page objects read these constants - step
 * definitions never touch them.
 *
 * <pre>
 *   page.locator(Locators.Login.USERNAME_FIELD)
 *   page.locator(Locators.Customers.SEARCH_BOX)
 * </pre>
 */
public final class Locators {

    private Locators() {
    }

    // =====================================================================
    // Login page - https://the-internet.herokuapp.com/login
    // =====================================================================
    public static final class Login {

        private Login() {
        }

        /** Username input on the login form. */
        public static final String USERNAME_FIELD = "#username";

        /** Password input on the login form. */
        public static final String PASSWORD_FIELD = "#password";

        /** Submit button of the login form. */
        public static final String LOGIN_BUTTON = "button[type='submit']";

        /** Flash banner showing the success or error message after submit. */
        public static final String FLASH_MESSAGE = "#flash";

        /** Logout link, rendered only once the user is authenticated. */
        public static final String LOGOUT_BUTTON = "a[href='/logout']";

        /** Page heading (h2) of the login screen. */
        public static final String HEADING = "h2";
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
        public static final String PAGE_HEADING = "h1.accounts-title";

        /** "Create account" action button. */
        public static final String CREATE_BUTTON = "button[data-test='create-account']";

        /** Table listing the accounts. */
        public static final String ACCOUNTS_TABLE = "table#accounts";

        /** Row selector within the accounts table. */
        public static final String ACCOUNT_ROW = "table#accounts tbody tr";
    }

    // =====================================================================
    // Customers page
    // =====================================================================
    public static final class Customers {

        private Customers() {
        }

        /** Heading of the customers list screen. */
        public static final String PAGE_HEADING = "h1.customers-title";

        /** Search input used to filter customers. */
        public static final String SEARCH_BOX = "input[data-test='customer-search']";

        /** "Add customer" action button. */
        public static final String ADD_BUTTON = "button[data-test='add-customer']";

        /** Row selector within the customers table. */
        public static final String CUSTOMER_ROW = "table#customers tbody tr";

        /** Per-row edit action. */
        public static final String EDIT_ROW_BUTTON = "button[data-test='edit-customer']";
    }
}