package com.satviklabs.customLocators;

import java.util.Map;

/**
 * Locators for the customer page, fed by {@code reads_CustomCustomer.csv}.
 */
public final class ByCustomerLocators {

    /**
     * Whole Customer.csv as key -&gt; selector. Loaded once by the static block.
     */
    public static final Map<String, String> locators;

    static {
        locators = LoadLocators.ctors(LoadLocators.Element.CUSTOMER);
    }

    /** Selector lookup into {@link #locators}. */
    public static class By extends LoadLocators {

        public static final String PAGE_HEADING = get(locators, "pageHeading");
        public static final String SEARCH_BOX = get(locators, "searchBox");
        public static final String ADD_BUTTON = get(locators, "addButton");
        public static final String CUSTOMER_ROW = get(locators, "customerRow");
        public static final String EDIT_ROW_BUTTON = get(locators, "editRowButton");
        public static final String DELETE_ROW_BUTTON = get(locators, "deleteRowButton");
    }

    private ByCustomerLocators() {
    }
}