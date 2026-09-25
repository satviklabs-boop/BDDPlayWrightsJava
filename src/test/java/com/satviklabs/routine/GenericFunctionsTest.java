package com.satviklabs.routine;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the browser/data helpers on {@link GenericFunctions}.
 *
 * <p>{@link #generateRandomNumber} is checked exhaustively for range and bounds.
 * The window and dialog helpers run against a real headless browser, because
 * their whole job is to talk to one, and a mock would not prove the tabs or the
 * dialog are actually handled.
 */
class GenericFunctionsTest {

    private static Playwright playwright;
    private static Browser browser;
    private static BrowserContext context;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true));
        context = browser.newContext();
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    // --------------------------------------------------------- random number

    @Test
    void randomNumberStaysWithinInclusiveBounds() {
        for (int i = 0; i < 500; i++) {
            int value = GenericFunctions.generateRandomNumber(10, 20);
            assertThat(value).isBetween(10, 20);
        }
    }

    @Test
    void randomNumberCanProduceBothBounds() {
        // Not a distribution test: with 500 draws over a 2-wide range, missing an
        // endpoint would mean the range is off by one, which is the bug worth
        // catching here.
        boolean sawMin = false;
        boolean sawMax = false;
        for (int i = 0; i < 500; i++) {
            int value = GenericFunctions.generateRandomNumber(1, 2);
            sawMin |= value == 1;
            sawMax |= value == 2;
        }
        assertThat(sawMin).as("lower bound never returned").isTrue();
        assertThat(sawMax).as("upper bound never returned").isTrue();
    }

    @Test
    void singleArgumentFormStartsAtOne() {
        for (int i = 0; i < 500; i++) {
            assertThat(GenericFunctions.generateRandomNumber(5)).isBetween(1, 5);
        }
    }

    @Test
    void equalBoundsReturnThatValue() {
        assertThat(GenericFunctions.generateRandomNumber(7, 7)).isEqualTo(7);
    }

    @Test
    void reversedBoundsAreRejected() {
        assertThatThrownBy(() -> GenericFunctions.generateRandomNumber(10, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than max");
    }

    @Test
    void maxBelowOneIsRejected() {
        assertThatThrownBy(() -> GenericFunctions.generateRandomNumber(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1");
    }

    // -------------------------------------------------------- window handles

    @Test
    void windowHandlesReportsTheOpenTabs() {
        Page page = context.newPage();
        try {
            assertThat(GenericFunctions.windowHandles(page)).hasSize(1);
            assertThat(GenericFunctions.windowHandleCount(page)).isEqualTo(1);

            Page second = context.newPage();
            try {
                assertThat(GenericFunctions.windowHandles(page)).hasSize(2);
                assertThat(GenericFunctions.windowHandleCount(page)).isEqualTo(2);
                // Index 0 stays the original tab, which is what makes an index
                // captured before an action still valid afterwards.
                assertThat(GenericFunctions.windowHandles(page).get(0)).isSameAs(page);
            } finally {
                second.close();
            }

            assertThat(GenericFunctions.windowHandles(page)).hasSize(1);
        } finally {
            page.close();
        }
    }

    @Test
    void windowHandlesRejectsNull() {
        assertThatThrownBy(() -> GenericFunctions.windowHandles(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page must not be null");
    }

    // -------------------------------------------------------------- dialogs

    @Test
    void alertIsAcceptedAndDoesNotBlock() {
        Page page = context.newPage();
        try {
            page.setContent("<button id='go' onclick=\"alert('hello')\">go</button>");
            try (AutoCloseable ignored = GenericFunctions.acceptAlertIfPresent(page)) {
                page.click("#go");
                // The click returns only if the dialog was handled. If it were
                // left open, this assertion would never be reached - the click
                // itself would hang.
                assertThat(page.locator("#go").isVisible()).isTrue();
            }
        } catch (Exception e) {
            throw new AssertionError("Dialog handling failed", e);
        } finally {
            page.close();
        }
    }

    @Test
    void pageStillUsableWhenNoDialogAppears() {
        Page page = context.newPage();
        try {
            page.setContent("<button id='go'>go</button>");
            try (AutoCloseable ignored = GenericFunctions.acceptAlertIfPresent(page)) {
                page.click("#go");
                assertThat(page.locator("#go").isVisible()).isTrue();
            }
        } catch (Exception e) {
            throw new AssertionError("Registering the handler broke a normal click", e);
        } finally {
            page.close();
        }
    }

    @Test
    void closeIsIdempotent() throws Exception {
        Page page = context.newPage();
        try {
            AutoCloseable handle = GenericFunctions.acceptAlertIfPresent(page);
            handle.close();
            handle.close();
            assertThat(GenericFunctions.windowHandles(page)).isNotEmpty();
        } finally {
            page.close();
        }
    }

    @Test
    void acceptAlertRejectsNull() {
        assertThatThrownBy(() -> GenericFunctions.acceptAlertIfPresent(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page must not be null");
    }

    /** The handles list is a snapshot, so mutating it cannot corrupt the context. */
    @Test
    void returnedHandleListIsImmutable() {
        Page page = context.newPage();
        try {
            List<Page> handles = GenericFunctions.windowHandles(page);
            assertThatThrownBy(() -> handles.add(page))
                    .isInstanceOf(UnsupportedOperationException.class);
        } finally {
            page.close();
        }
    }
}