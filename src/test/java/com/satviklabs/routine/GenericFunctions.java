package com.satviklabs.routine;

import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Page;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Shared helpers: locator loading, plus small routines used across page objects
 * and step definitions.
 *
 * <h2>Locators</h2>
 *
 * <p>One CSV per screen, in {@code src/test/resources/locators/}:
 *
 * <pre>
 *   locators/login.csv
 *   locators/account.csv
 *   locators/customer.csv
 * </pre>
 *
 * <p>Each row is {@code key,selector}. Blank lines and rows starting with
 * {@code #} are ignored. Only the first comma separates key from selector, so a
 * selector may itself contain commas. Keys are matched case-insensitively.
 *
 * <pre>
 *   usernameField,#username
 *   loginButton,button[type='submit']
 * </pre>
 *
 * <h2>Using it</h2>
 *
 * <p>A page object loads its CSV once into a static field and looks selectors up
 * by key:
 *
 * <pre>
 *   public class LoginPage extends BasePage {
 *
 *       private static final Map<String, String> LOCATORS =
 *               GenericFunctions.loadLocators("login");
 *
 *       private Locator usernameField() {
 *           return page.locator(GenericFunctions.get(LOCATORS, "usernameField"));
 *       }
 *   }
 * </pre>
 *
 * <p>Both lookups throw with the list of available keys when a key is missing, so
 * a typo fails immediately instead of as a 90-second Playwright timeout on a
 * selector that quietly matched nothing.
 */
public final class GenericFunctions {

    /** Folder holding the locator CSVs, relative to the classpath root. */
    private static final String LOCATORS_FOLDER = "locators";

    private static final String FILE_SUFFIX = ".csv";

    /** file path -> parsed key/selector map. Each CSV is read once per JVM. */
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private GenericFunctions() {
    }

    /**
     * Reads {@code locators/<pageName>.csv} and returns its key/selector map.
     *
     * <p>Assign it to a static field, once per page:
     *
     * <pre>
     *   private static final Map<String, String> LOCATORS =
     *           GenericFunctions.loadLocators("login");
     * </pre>
     *
     * <p>The file is parsed once per JVM, so this and repeated calls for the same
     * page cost a map lookup.
     *
     * @param pageName the CSV base name, e.g. {@code "login"} for
     *                 {@code locators/login.csv}
     * @return the page's key -> selector map
     * @throws IllegalStateException when the file is absent, empty or malformed
     */
    public static Map<String, String> loadLocators(String pageName) {
        if (isBlank(pageName)) {
            throw new IllegalStateException("Locator page name must not be empty");
        }
        String path = LOCATORS_FOLDER + "/" + pageName.trim() + FILE_SUFFIX;
        return CACHE.computeIfAbsent(path, GenericFunctions::read);
    }

    /**
     * Looks up one selector in a loaded page map.
     *
     * <pre>
     *   String selector = GenericFunctions.get(LOCATORS, "usernameField");
     * </pre>
     *
     * @param pageLocators a map from {@link #loadLocators(String)}
     * @param key          the CSV key, e.g. {@code "usernameField"}
     * @return the selector for that key
     * @throws IllegalStateException when the map is null or the key is absent, so
     *                               a typo surfaces immediately instead of as a
     *                               Playwright timeout
     */
    public static String get(Map<String, String> pageLocators, String key) {
        if (pageLocators == null) {
            throw new IllegalStateException(
                    "Locator map is null - was GenericFunctions.loadLocators(...) "
                            + "assigned to a static field?");
        }
        String selector = pageLocators.get(normalise(key));
        if (selector == null || selector.isEmpty()) {
            throw new IllegalStateException(
                    "Missing locator '" + key + "' (available keys: "
                            + pageLocators.keySet() + ")");
        }
        return selector;
    }

    private static String normalise(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------- random data

    /**
     * A random whole number in the inclusive range {@code [min, max]}.
     *
     * <p>Handy for building data that must be unique per run, so a test does not
     * collide with what a previous run left behind:
     *
     * <pre>
     *   String email = "user" + generateRandomNumber(1000, 9999) + "@example.com";
     * </pre>
     *
     * <p>Uses {@link ThreadLocalRandom}, so scenarios running in parallel cannot
     * draw the same value from a shared seed.
     *
     * @param min smallest value that may be returned (inclusive)
     * @param max largest value that may be returned (inclusive)
     * @return a random int between {@code min} and {@code max}, both inclusive
     * @throws IllegalArgumentException when {@code min} is greater than
     *                                  {@code max}
     */
    public static int generateRandomNumber(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException(
                    "min (" + min + ") must not be greater than max (" + max + ")");
        }
        if (min == max) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * A random whole number from {@code 1} to {@code max} inclusive, the common
     * case for a suffix or an index.
     *
     * @param max largest value that may be returned (inclusive); must be at least 1
     * @throws IllegalArgumentException when {@code max} is less than 1
     */
    public static int generateRandomNumber(int max) {
        if (max < 1) {
            throw new IllegalArgumentException("max must be at least 1, was " + max);
        }
        return generateRandomNumber(1, max);
    }

    // ------------------------------------------------------ browser handles

    /**
     * Every window/tab currently open in the browser context, in the order the
     * browser reports them.
     *
     * <p>Index 0 is the first tab from the context's point of view, which is
     * normally the one the scenario started in. Use this when a click opens a new
     * tab and the test needs to hand control to it:
     *
     * <pre>
     *   int before = windowHandleCount(page);
     *   page.click("a[target='_blank']");
     *   page.waitForCondition(() -> windowHandleCount(page) == before + 1);
     *   Page newTab = page.context().pages().get(before);
     *   newTab.bringToFront();
     * </pre>
     *
     * <p>Returns {@link Page} objects rather than raw window handle strings: a
     * handle is only useful once resolved to a {@code Page} that can be driven,
     * and Playwright already hands them over in that form. The order is stable for
     * the life of the context, so an index taken before an action still refers to
     * the same tab afterwards - which is what makes the snippet above safe.
     *
     * @param page the page whose browser context to inspect
     * @return the open tabs; never null, empty once the context has been closed
     */
    public static List<Page> windowHandles(Page page) {
        if (page == null) {
            throw new IllegalArgumentException("page must not be null");
        }
        return List.copyOf(page.context().pages());
    }

    /**
     * The number of windows/tabs currently open in the browser context.
     *
     * <p>Usually more useful than the handles themselves, e.g. to assert that a
     * link opened exactly one new tab:
     *
     * <pre>
     *   int before = windowHandleCount(page);
     *   page.click("a[target='_blank']");
     *   page.waitForCondition(() -> windowHandleCount(page) == before + 1);
     * </pre>
     *
     * @param page the page whose browser context to inspect
     * @return how many tabs are open
     */
    public static int windowHandleCount(Page page) {
        if (page == null) {
            throw new IllegalArgumentException("page must not be null");
        }
        return page.context().pages().size();
    }

    /**
     * Registers a handler that dismisses any JavaScript dialog that appears, and
     * removes it again when done.
     *
     * <p>A native {@code alert}/{@code confirm}/{@code prompt} blocks the page
     * until it is handled, so an unhandled one turns into a Playwright timeout
     * with no obvious cause. Register this <em>before</em> the action that
     * triggers the dialog.
     *
     * <p>Prefer this only when a dialog <em>may</em> appear and the test does not
     * care about its content. To assert on a specific dialog, use
     * {@code page.onDialog(...)} directly so the message can be checked.
     *
     * <pre>
     *   try (AutoCloseable ignored = acceptAlertIfPresent(page)) {
     *       page.click("button#delete");
     *   }
     * </pre>
     *
     * <p>Returned as {@link AutoCloseable} so it can be used in try-with-resources
     * and the handler is always removed. Calling {@code close()} twice is safe.
     *
     * @param page the page to watch for dialogs
     * @return a handle that removes the listener on {@code close()}
     */
    public static AutoCloseable acceptAlertIfPresent(Page page) {
        if (page == null) {
            throw new IllegalArgumentException("page must not be null");
        }
        Consumer<Dialog> handler = dialog -> {
            try {
                dialog.accept();
            } catch (RuntimeException e) {
                // The dialog was already dismissed by the page itself, or the tab
                // closed underneath it. Nothing useful to do, and rethrowing would
                // fail a scenario over a non-assertion.
            }
        };
        // Registration removes itself after the first dialog, so a handler cannot
        // act twice on one prompt - Playwright rejects accept() on a dialog that
        // has already been handled.
        page.onceDialog(handler);

        return () -> {
            try {
                page.offDialog(handler);
            } catch (RuntimeException e) {
                // Context already closed; the listener is gone with it.
            }
        };
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Resolves and parses one CSV from the classpath. */
    private static Map<String, String> read(String csvPath) {
        String resource = csvPath.startsWith("/") ? csvPath : "/" + csvPath;

        try (InputStream in = GenericFunctions.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Locator file '" + csvPath + "' not found on the classpath "
                                + "as '" + resource + "'. Expected it in "
                                + "src/test/resources/" + csvPath + ".");
            }
            Map<String, String> entries = new LinkedHashMap<>();
            parse(in, resource, entries);
            return entries;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Unable to read locator file '" + resource + "'", e);
        }
    }

    /** Parses {@code key,selector} rows, skipping blanks and {@code #} comments. */
    private static void parse(InputStream in, String source, Map<String, String> entries)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().toList();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int comma = line.indexOf(',');
                if (comma < 1) {
                    throw new IllegalStateException(
                            "Malformed row in " + source + " line " + (i + 1)
                                    + ": expected 'key,selector' but found '" + line + "'");
                }
                String key = normalise(line.substring(0, comma));
                String selector = line.substring(comma + 1).trim();
                entries.put(key, selector);
            }
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException(
                    "Locator file '" + source + "' contains no entries");
        }
    }
}