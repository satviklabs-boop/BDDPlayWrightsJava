package com.satviklabs.customLocators;

/**
 * Locators for the account page, fed by {@code reads_CustomAccount.csv}.
 */
public final class ByAccountsLocators extends LoadLocators {

    static {
        loadLocators("Account");
    }

    /** Selector lookup into this page's CSV. */
    public static class By extends LoadLocators {

        /** Must come first - see {@code ByLoginLocators.By}. */
        static {
            loadLocators("Account");
        }

        public static final String PAGE_HEADING = get("pageHeading");
        public static final String CREATE_BUTTON = get("createButton");
        public static final String ACCOUNTS_TABLE = get("accountsTable");
        public static final String ACCOUNT_ROW = get("accountRow");
        public static final String CREATE_ACCOUNT_BUTTON = get("createAccountButton");

        private By() {
        }
    }

    private ByAccountsLocators() {
    }
}