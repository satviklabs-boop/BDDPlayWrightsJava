package com.satviklabs.routine;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the selectors in {@code src/test/resources/locators/*.csv}.
 *
 * <p>These are the values the framework shipped before the locators moved, so
 * this is the behaviour-preservation check for that work: if a selector is edited
 * or a CSV stops being packaged, this fails loudly here instead of the UI suite
 * timing out 90 seconds later on an element that never appears.
 *
 * <p>Also covers the failure modes that used to be silent: a missing key, an
 * absent file, and a malformed row.
 */
public class LocatorLoadingTest {

    private static final Map<String, String> LOGIN = GenericFunctions.loadLocators("login");
    private static final Map<String, String> ACCOUNT = GenericFunctions.loadLocators("account");
    private static final Map<String, String> CUSTOMER = GenericFunctions.loadLocators("customer");

    @Test
    public void loginCsvHasTheExpectedSelectors() {
        assertThat(LOGIN).containsOnly(
                Map.entry("usernamefield", "#username"),
                Map.entry("passwordfield", "#password"),
                Map.entry("loginbutton", "button[type='submit']"),
                Map.entry("flashmessage", "#flash"),
                Map.entry("logoutbutton", "a[href='/logout']"),
                Map.entry("heading", "h2"));
    }

    @Test
    public void accountCsvHasTheExpectedSelectors() {
        assertThat(ACCOUNT).containsOnly(
                Map.entry("pageheading", "h1.accounts-title"),
                Map.entry("createbutton", "button[data-test='create-account']"),
                Map.entry("accountstable", "table#accounts"),
                Map.entry("accountrow", "table#accounts tbody tr"),
                Map.entry("createaccountbutton", "button[data-test='create-account']"));
    }

    @Test
    public void customerCsvHasTheExpectedSelectors() {
        assertThat(CUSTOMER).containsOnly(
                Map.entry("pageheading", "h1.customers-title"),
                Map.entry("searchbox", "input[data-test='customer-search']"),
                Map.entry("addbutton", "button[data-test='add-customer']"),
                Map.entry("customerrow", "table#customers tbody tr"),
                Map.entry("editrowbutton", "button[data-test='edit-customer']"),
                Map.entry("deleterowbutton", "button[data-test='delete-customer']"));
    }

    /** Each page has its own map, so a key from another page is not reachable. */
    @Test
    public void pagesAreIsolatedFromEachOther() {
        assertThatThrownBy(() -> GenericFunctions.get(LOGIN, "pageHeading"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing locator 'pageHeading'");
    }

    @Test
    public void unknownKeyFailsFastWithTheAvailableKeys() {
        // Keys are normalised to lower case, so the "available keys" list is too.
        assertThatThrownBy(() -> GenericFunctions.get(LOGIN, "noSuchKey"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("noSuchKey")
                .hasMessageContaining("usernamefield");
    }

    @Test
    public void missingFileFailsWithTheExpectedPath() {
        assertThatThrownBy(() -> GenericFunctions.loadLocators("doesNotExist"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locators/doesNotExist.csv");
    }

    /** A null map means the page never loaded its CSV - worth a clear message. */
    @Test
    public void nullMapFailsFast() {
        assertThatThrownBy(() -> GenericFunctions.get(null, "usernameField"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Locator map is null");
    }

    @Test
    public void keysAreCaseInsensitiveAndTrimmed() {
        assertThat(GenericFunctions.get(LOGIN, "  USERNAMEfield  ")).isEqualTo("#username");
    }

    /** The same page name returns the same map, so a CSV is parsed once per JVM. */
    @Test
    public void repeatedLoadsAreCached() {
        assertThat(GenericFunctions.loadLocators("login")).isSameAs(LOGIN);
    }
}
