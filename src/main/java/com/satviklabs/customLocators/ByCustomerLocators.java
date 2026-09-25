package com.satviklabs.customLocators;

/**
 * Locators for the customer page, fed by {@code reads_CustomCustomer.csv}.
 */
public final class ByCustomerLocators extends LoadLocators {

    static {
        loadLocators("Customer");
    }

    /** Selector lookup into this page's CSV. */
    public static class By extends LoadLocators {

        /** Must come first - see {@code ByLoginLocators.By}. */
        static {
            loadLocators("Customer");
        }

        public static final String PAGE_HEADING = get("pageHeading");
        public static final String SEARCH_BOX = get("searchBox");
        public static final String ADD_BUTTON = get("addButton");
        public static final String CUSTOMER_ROW = get("customerRow");
        public static final String EDIT_ROW_BUTTON = get("editRowButton");
        public static final String DELETE_ROW_BUTTON = get("deleteRowButton");

        private By() {
        }
    }

    private ByCustomerLocators() {
    }
}