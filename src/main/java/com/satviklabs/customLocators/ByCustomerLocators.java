package com.satviklabs.customLocators;

import java.util.Map;

/**
 * Locators for the customer page, fed by {@code reads_CustomCustomer.csv}.
 *
 * <p>This page's {@link #LOCATORS} map is passed explicitly to every lookup, so it
 * can never resolve a key from another page's CSV.
 */
public final class ByCustomerLocators extends LoadLocators {

    /** {@code reads_CustomCustomer.csv} as key -&gt; selector. Loaded once. */
    public static final Map<String, String> LOCATORS = ctors(Element.CUSTOMER);

    /** Selector lookup into {@link #LOCATORS}. */
    public static class By extends LoadLocators {

        public static final String PAGE_HEADING = select(LOCATORS, "pageHeading");
        public static final String SEARCH_BOX = select(LOCATORS, "searchBox");
        public static final String ADD_BUTTON = select(LOCATORS, "addButton");
        public static final String CUSTOMER_ROW = select(LOCATORS, "customerRow");
        public static final String EDIT_ROW_BUTTON = select(LOCATORS, "editRowButton");
        public static final String DELETE_ROW_BUTTON = select(LOCATORS, "deleteRowButton");

        private By() {
        }
    }

    private ByCustomerLocators() {
    }
}