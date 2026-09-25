package com.satviklabs.customLocators;

import com.satviklabs.routine.GenericFunctions;

/**
 * Locators for the customer page, fed by {@code locators/customer.csv}.
 */
public final class CustomerLocators {

    /** Must come first - see {@code LoginLocators}. */
    static {
        GenericFunctions.loadLocators("customer");
    }

    public static final String PAGE_HEADING = GenericFunctions.get("pageHeading");
    public static final String SEARCH_BOX = GenericFunctions.get("searchBox");
    public static final String ADD_BUTTON = GenericFunctions.get("addButton");
    public static final String CUSTOMER_ROW = GenericFunctions.get("customerRow");
    public static final String EDIT_ROW_BUTTON = GenericFunctions.get("editRowButton");
    public static final String DELETE_ROW_BUTTON = GenericFunctions.get("deleteRowButton");

    private CustomerLocators() {
    }
}