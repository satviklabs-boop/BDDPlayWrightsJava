package com.satviklabs.customLocators;

import java.util.Map;

/**
 * Locators for the account page, fed by {@code reads_CustomAccount.csv}.
 *
 * <p>This page's {@link #LOCATORS} map is passed explicitly to every lookup, so it
 * can never resolve a key from another page's CSV.
 */
public final class ByAccountsLocators extends LoadLocators {

    /** {@code reads_CustomAccount.csv} as key -&gt; selector. Loaded once. */
    public static final Map<String, String> LOCATORS = ctors(Element.ACCOUNT);

    /** Selector lookup into {@link #LOCATORS}. */
    public static class By extends LoadLocators {

        public static final String PAGE_HEADING = select(LOCATORS, "pageHeading");
        public static final String CREATE_BUTTON = select(LOCATORS, "createButton");
        public static final String ACCOUNTS_TABLE = select(LOCATORS, "accountsTable");
        public static final String ACCOUNT_ROW = select(LOCATORS, "accountRow");
        public static final String CREATE_ACCOUNT_BUTTON =
                select(LOCATORS, "createAccountButton");

        private By() {
        }
    }

    private ByAccountsLocators() {
    }
}