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
 * Shared helper routines. Currently this is the locator loader.
 *
 * <h2>How to use it</h2>
 *
 * <p>Locator CSVs live in {@code src/test/resources/locators/}, one per screen:
 *
 * <pre>
 *   locators/login.csv
 *   locators/account.csv
 *   locators/customer.csv
 * </pre>
 *
 * <p>Each row is {@code key,selector}. Rows starting with {@code #} are
 * comments, and only the first comma separates key from selector, so a selector
 * may itself contain commas:
 *
 * <pre>
 *   usernameField,#username
 *   loginButton,button[type='submit']
 * </pre>
 *
 * <p>A page's locator class loads its CSV once in a static block and exposes the
 * values as plain constants, so a renamed key fails at compile time rather than
 * as a Playwright timeout:
 *
 * <pre>
 *   public final class LoginLocators {
 *       static {
 *           GenericFunctions.loadLocators("login");
 *       }
 *       public static final String USERNAME_FIELD =
 *               GenericFunctions.get("usernameField");
 *   }
 * </pre>
 */
public final class GenericFunctions {

    /** Folder holding the locator CSVs, relative to the classpath root. */
    private static final String LOCATORS_FOLDER = "locators";

    private static final String FILE_SUFFIX = ".csv";

    /**
     * Overrides {@link #LOCATORS_FOLDER}. Set with
     * {@code -DlocatorsFolderPath=...} to point at a folder of CSVs outside the
     * jar, e.g. to hot-fix a selector on a CI box without a rebuild.
     */
    private static final String FOLDER_PROPERTY = "locatorsFolderPath";

    /** file name -> parsed key/selector map. Read once per JVM. */
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    /**
     * caller class name -> the map that class loaded.
     *
     * <p>Keyed by the <em>calling class</em>, which is what makes
     * {@link #get(String)} deterministic: a page resolves against its own CSV
     * and can never read another page's selectors.
     */
    private static final Map<String, Map<String, String>> LOADED = new ConcurrentHashMap<>();

    private GenericFunctions() {
    }

    /**
     * Reads {@code locators/<name>.csv} and binds it to the calling class.
     *
     * <p>Call once from the static initializer of a page's locator class:
     *
     * <pre>
     *   static {
     *       loadLocators("login");
     *   }
     * </pre>
     *
     * <p>The binding is per calling class, so {@code LoginLocators} calling
     * {@code loadLocators("login")} and {@code AccountLocators} calling
     * {@code loadLocators("account")} each see only their own selectors. Put the
     * block <em>above</em> the constants that use it - static initializers run in
     * textual order.
     *
     * @param locatorName the CSV base name, e.g. {@code "login"} for
     *                    {@code locators/login.csv}
     * @return this page's key/selector map, so a page object can hold one
     *         reference and look up any of its own selectors, e.g.
     *         {@code LOCATORS.get("usernameField")}
     * @throws IllegalStateException when the CSV is absent, empty or malformed
     */
    public static Map<String, String> loadLocators(String locatorName) {
        if (isBlank(locatorName)) {
            throw new IllegalStateException("Locator name must not be empty");
        }
        Map<String, String> locators = configureLocators(
                folderPath() + "/" + locatorName.trim() + FILE_SUFFIX);
        LOADED.put(callerClassName(), locators);
        return locators;
    }

    /**
     * Reads a locator CSV and returns its {@code key,selector} map.
     *
     * <p>This is the file-reading half of the API: it takes a path relative to
     * the classpath root and does no binding, so it can be used for a sheet that
     * is not tied to one page. Most callers want {@link #loadLocators(String)},
     * which derives the path and binds the result.
     *
     * <p>The result is parsed once per JVM and cached, so repeated calls for the
     * same file cost a map lookup.
     *
     * <pre>
     *   Map&lt;String, String&gt; login =
     *           configureLocators("locators/login.csv");
     * </pre>
     *
     * @param csvPath path relative to the classpath root, e.g.
     *                {@code "locators/login.csv"}; a leading {@code /} is
     *                optional
     * @throws IllegalStateException when the file is absent, empty or malformed
     */
    public static Map<String, String> configureLocators(String csvPath) {
        if (isBlank(csvPath)) {
            throw new IllegalStateException("Locator file path must not be empty");
        }
        String fileName = csvPath.trim().replace('\\', '/');
        return CACHE.computeIfAbsent(fileName, GenericFunctions::read);
    }

    /**
     * Looks up a selector in the calling class's own loaded CSV.
     *
     * <pre>
     *   public static final String USERNAME_FIELD = get("usernameField");
     * </pre>
     *
     * @param key the CSV key, e.g. {@code "usernameField"}
     * @throws IllegalStateException when the calling class has not run
     *                               {@link #loadLocators(String)} yet, or the key
     *                               is absent - so a typo surfaces immediately
     *                               instead of as a Playwright timeout
     */
    public static String get(String key) {
        String caller = callerClassName();
        Map<String, String> pageLocators = LOADED.get(caller);
        if (pageLocators == null) {
            throw new IllegalStateException(
                    "No locators loaded for " + caller + ". Add a static block "
                            + "above the constants that use it:\n"
                            + "    static { GenericFunctions.loadLocators(\"<name>\"); }");
        }
        return get(pageLocators, key);
    }

    /**
     * Looks up one selector from an explicit page map.
     *
     * <pre>
     *   String selector = GenericFunctions.get(locators, "usernameField");
     * </pre>
     *
     * @throws IllegalStateException when the map is null or the key is absent
     */
    public static String get(Map<String, String> pageLocators, String key) {
        if (pageLocators == null) {
            throw new IllegalStateException(
                    "Locator map is null - was loadLocators(...) run in the static "
                            + "initializer of this page's locator class?");
        }
        String selector = pageLocators.get(normalise(key));
        if (selector == null || selector.isEmpty()) {
            throw new IllegalStateException(
                    "Missing locator '" + key + "' (available keys: "
                            + pageLocators.keySet() + ")");
        }
        return selector;
    }

    /**
     * The calling class: the first frame outside this class on the stack.
     *
     * <p>This is what binds a {@link #loadLocators(String)} call to the class
     * that made it, so {@link #get(String)} is a plain, deterministic map lookup
     * with no guessing about which page a key belongs to.
     */
    private static String callerClassName() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .filter(f -> !GenericFunctions.class.equals(f.getDeclaringClass()))
                        .map(f -> f.getDeclaringClass().getName())
                        .findFirst()
                        .orElse(GenericFunctions.class.getName()));
    }

    private static String normalise(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Folder holding the CSVs, from {@code -DlocatorsFolderPath=...} or
     * {@code locatorsFolderPath} as an environment variable, defaulting to
     * {@link #LOCATORS_FOLDER}.
     */
    private static String folderPath() {
        String value = System.getProperty(FOLDER_PROPERTY);
        if (isBlank(value)) {
            value = System.getenv(FOLDER_PROPERTY);
        }
        return isBlank(value)
                ? LOCATORS_FOLDER
                : value.trim().replace('\\', '/');
    }

    /**
     * Resolves and parses one CSV from the classpath.
     *
     * @param csvPath path relative to the classpath root, e.g.
     *                {@code locators/login.csv}; the {@code locatorsFolderPath}
     *                override is applied when the caller used
     *                {@link #loadLocators(String)}, not here
     */
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
            throw new IllegalStateException("Locator file '" + source + "' contains no entries");
        }
    }
}