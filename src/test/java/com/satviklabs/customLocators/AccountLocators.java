package com.satviklabs.customLocators;

import com.satviklabs.routine.GenericFunctions;

/**
 * Locators for the account page, fed by {@code locators/account.csv}.
 */
public final class AccountLocators {

    /** Must come first - see {@code LoginLocators}. */
    static {
        GenericFunctions.loadLocators("account");
    }

    public static final String PAGE_HEADING = GenericFunctions.get("pageHeading");
    public static final String CREATE_BUTTON = GenericFunctions.get("createButton");
    public static final String ACCOUNTS_TABLE = GenericFunctions.get("accountsTable");
    public static final String ACCOUNT_ROW = GenericFunctions.get("accountRow");
    public static final String CREATE_ACCOUNT_BUTTON = GenericFunctions.get("createAccountButton");

    private AccountLocators() {
    }
}