package com.satviklabs.customLocators;

import java.util.Map;

/**
 * Locators for the account page, fed by {@code reads_CustomAccount.csv}.
 */
public final class ByAccountsLocators {

    /** Whole Account.csv as key -&gt; selector. Loaded once by the static block. */
    public static final Map<String, String> locators;

    static {
        locators = LoadLocators.ctors(LoadLocators.Element.ACCOUNT);
    }

    /** Selector lookup into {@link #locators}. */
    public static class By extends LoadLocators {

        public static final String PAGE_HEADING = get(locators, "pageHeading");
        public static final String CREATE_BUTTON = get(locators, "createButton");
        public static final String ACCOUNTS_TABLE = get(locators, "accountsTable");
        public static final String ACCOUNT_ROW = get(locators, "accountRow");
        public static final String CREATE_ACCOUNT_BUTTON = get(locators, "createAccountButton");
    }

    private ByAccountsLocators() {
    }
}