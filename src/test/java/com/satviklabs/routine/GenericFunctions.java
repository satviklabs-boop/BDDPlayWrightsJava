package com.satviklabs.routine;

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

/**
 * Loads a page's selectors from a CSV file on the classpath.
 *
 * <h2>The files</h2>
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
 *       private static final Map&lt;String, String&gt; LOCATORS =
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
     *   private static final Map&lt;String, String&gt; LOCATORS =
     *           GenericFunctions.loadLocators("login");
     * </pre>
     *
     * <p>The file is parsed once per JVM, so this and repeated calls for the same
     * page cost a map lookup.
     *
     * @param pageName the CSV base name, e.g. {@code "login"} for
     *                 {@code locators/login.csv}
     * @return the page's key -&gt; selector map
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