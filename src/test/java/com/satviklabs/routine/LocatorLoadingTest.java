package com.satviklabs.routine;

import com.satviklabs.customLocators.AccountLocators;
import com.satviklabs.customLocators.CustomerLocators;
import com.satviklabs.customLocators.LoginLocators;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the selectors loaded from {@code src/test/resources/locators/*.csv}.
 *
 * <p>These are the exact values the framework shipped before the locators moved
 * out of {@code src/main/java/com/satviklabs/customLocators}, so this test is the
 * behaviour-preservation check for that move: if a selector is edited or a CSV
 * stops being packaged, this fails loudly instead of the UI suite timing out.
 *
 * <p>It also covers the two failure modes that used to be silent - a class that
 * never ran its static block, and a typo'd key.
 */
class LocatorLoadingTest {

    @Test
    void loginLocatorsMatchTheOriginalCsv() {
        assertThat(LoginLocators.USERNAME_FIELD).isEqualTo("#username");
        assertThat(LoginLocators.PASSWORD_FIELD).isEqualTo("#password");
        assertThat(LoginLocators.LOGIN_BUTTON).isEqualTo("button[type='submit']");
        assertThat(LoginLocators.FLASH_MESSAGE).isEqualTo("#flash");
        assertThat(LoginLocators.LOGOUT_BUTTON).isEqualTo("a[href='/logout']");
        assertThat(LoginLocators.HEADING).isEqualTo("h2");
    }

    @Test
    void accountLocatorsMatchTheOriginalCsv() {
        assertThat(AccountLocators.PAGE_HEADING).isEqualTo("h1.accounts-title");
        assertThat(AccountLocators.CREATE_BUTTON)
                .isEqualTo("button[data-test='create-account']");
        assertThat(AccountLocators.ACCOUNTS_TABLE).isEqualTo("table#accounts");
        assertThat(AccountLocators.ACCOUNT_ROW).isEqualTo("table#accounts tbody tr");
        assertThat(AccountLocators.CREATE_ACCOUNT_BUTTON)
                .isEqualTo("button[data-test='create-account']");
    }

    @Test
    void customerLocatorsMatchTheOriginalCsv() {
        assertThat(CustomerLocators.PAGE_HEADING).isEqualTo("h1.customers-title");
        assertThat(CustomerLocators.SEARCH_BOX)
                .isEqualTo("input[data-test='customer-search']");
        assertThat(CustomerLocators.ADD_BUTTON)
                .isEqualTo("button[data-test='add-customer']");
        assertThat(CustomerLocators.CUSTOMER_ROW).isEqualTo("table#customers tbody tr");
        assertThat(CustomerLocators.EDIT_ROW_BUTTON)
                .isEqualTo("button[data-test='edit-customer']");
        assertThat(CustomerLocators.DELETE_ROW_BUTTON)
                .isEqualTo("button[data-test='delete-customer']");
    }

    /** Pages stay isolated: a key from another page's CSV is not reachable. */
    @Test
    void eachPageSeesOnlyItsOwnCsv() {
        assertThatThrownBy(() -> GenericFunctions.get(
                GenericFunctions.configureLocators("locators/login.csv"), "pageHeading"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing locator 'pageHeading'");
    }

    @Test
    void unknownKeyFailsFastWithTheAvailableKeys() {
        // Keys are normalised to lower case, so the "available keys" list is too.
        assertThatThrownBy(() -> GenericFunctions.get(
                GenericFunctions.configureLocators("locators/login.csv"), "noSuchKey"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("noSuchKey")
                .hasMessageContaining("usernamefield");
    }

    @Test
    void missingCsvFailsWithTheExpectedPath() {
        assertThatThrownBy(() -> GenericFunctions.configureLocators("locators/doesNotExist.csv"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locators/doesNotExist.csv");
    }

    @Test
    void keysAreCaseInsensitiveAndTrimmed() {
        assertThat(GenericFunctions.get(
                GenericFunctions.configureLocators("locators/login.csv"),
                "  USERNAMEfield  "))
                .isEqualTo("#username");
    }
}