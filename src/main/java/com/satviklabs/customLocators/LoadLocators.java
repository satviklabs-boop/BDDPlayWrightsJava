package com.satviklabs.customLocators;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads a page's locator CSV and returns a key -> selector map.
 *
 * <p>Called once per page class from a static initializer, so the file is read a
 * single time per JVM and every {@code By} constant on that page is a plain
 * static field:
 *
 * <pre>
 *   public static Map&lt;String, String&gt; locators = LoadLocators.ctors(Element.LOGIN);
 * </pre>
 *
 * <p>One CSV per screen lives in {@code com/satviklabs/customLocators/}:
 *
 * <pre>
 *   reads_CustomLogin.csv      -&gt; Login page
 *   reads_CustomAccount.csv    -&gt; Account page
 *   reads_CustomCustomer.csv   -&gt; Customer page
 * </pre>
 *
 * <p>Each row is {@code key,selector}:
 *
 * <pre>
 *   usernameField,#username
 *   loginButton,button[type='submit']
 * </pre>
 *
 * <p>Rows start with {@code #} as comments. Only the first comma separates key
 * from selector, so a selector may itself contain commas. Keys are matched
 * case-insensitively.
 *
 * <p>The folder is configurable via {@code locatorsFolderPath} in
 * {@code config.properties} (or {@code -DlocatorsFolderPath=...}). The folder is
 * resolved on the classpath first and, failing that, as a filesystem path, so the
 * framework works both packaged (CSV inside the jar) and from a working copy
 * (CSV on disk, editable without a rebuild).
 */
public class LoadLocators {

    /** Page names accepted by {@link #loadLocators(String)}. */
    public static final class Element {

        public static final String LOGIN = "Login";
        public static final String ACCOUNT = "Account";
        public static final String CUSTOMER = "Customer";

        private Element() {
        }
    }

    private static final String FILE_PREFIX = "reads_Custom";
    private static final String FILE_SUFFIX = ".csv";

    /** Property name (system property, env var or config.properties) for the folder. */
    private static final String FOLDER_PROPERTY = "locatorsFolderPath";

    private static final String FOLDER_DEFAULT = "com/satviklabs/customLocators";

    private static final Properties FILE_PROPS = loadFileProps();

    /** csv file name -> its parsed key/selector map. Read once per JVM. */
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    /** page class name -> that page's own selectors, set by its static block. */
    private static final Map<String, Map<String, String>> PER_CLASS = new ConcurrentHashMap<>();

    /** Extendable only so page classes can write {@code class By extends LoadLocators}. */
    protected LoadLocators() {
    }

    /** Convenience entry point for callers that prefer the builder spelling. */
    public static LoadLocators load() {
        return new LoadLocators();
    }

    /**
     * Folder holding the locator CSVs, relative to the classpath root.
     *
     * <p>Read directly here rather than through ConfigLoader: this class lives in
     * {@code src/main/java} (so the CSVs travel inside the production jar), while
     * ConfigLoader is test-scoped and would not be on the classpath at
     * runtime. Resolution order is the same, system property first so
     * {@code -DlocatorsFolderPath=...} wins.
     */
    private static String folderPath() {
        String value = System.getProperty(FOLDER_PROPERTY);
        if (isBlank(value)) {
            value = System.getenv(FOLDER_PROPERTY);
        }
        if (isBlank(value)) {
            value = FILE_PROPS.getProperty(FOLDER_PROPERTY);
        }
        return isBlank(value) ? FOLDER_DEFAULT : value.trim().replace('\\', '/');
    }

    private static Properties loadFileProps() {
        Properties props = new Properties();
        try (InputStream in = LoadLocators.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read config.properties", e);
        }
        return props;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Loads the CSV named {@code page} for the calling page class.
     *
     * <p>Called from a page class's static block, exactly as in the reference
     * project:
     *
     * <pre>
     *   public class Login extends LoadLocators {
     *       static {
     *           loadLocators("Login");
     *       }
     *   }
     * </pre>
     *
     * <p>Unlike {@link #ctors(String)}, which hands the map back to the caller, this
     * stores it against the <em>calling class</em>. That is what keeps pages
     * separate: {@code Login} calling {@code loadLocators("Login")} and
     * {@code Account} calling {@code loadLocators("Account")} each end up with their
     * own map, and {@link #get(String)} only ever sees the caller's own selectors.
     * A single shared global would let any page read any other page's CSV.
     *
     * <p>The caller is identified by {@link StackWalker}, so a subclass cannot
     * accidentally overwrite its parent's (or another page's) entries.
     *
     * @param page the CSV base name, e.g. {@code "Login"} for
     *             {@code reads_CustomLogin.csv}
     * @throws IllegalStateException when the CSV is absent, empty or malformed
     */
    protected static void loadLocators(String page) {
        String caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .filter(f -> LoadLocators.class.isAssignableFrom(f.getDeclaringClass()))
                        .filter(f -> !LoadLocators.class.equals(f.getDeclaringClass()))
                        .map(f -> f.getDeclaringClass().getName())
                        .findFirst()
                        .orElse(LoadLocators.class.getName()));
        PER_CLASS.put(caller, ctors(page));
    }

    /**
     * Looks up a selector in the calling class's own loaded CSV.
     *
     * <pre>
     *   public static final String USERNAME_FIELD = get("usernameField");
     * </pre>
     *
     * @param key the CSV key, e.g. {@code "usernameField"}
     * @throws IllegalStateException when the page's static block did not run, or the
     *                               key is absent - so a typo surfaces immediately
     *                               instead of as a Playwright timeout
     */
    protected static String get(String key) {
        String caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .filter(f -> LoadLocators.class.isAssignableFrom(f.getDeclaringClass()))
                        .filter(f -> !LoadLocators.class.equals(f.getDeclaringClass()))
                        .map(f -> f.getDeclaringClass().getName())
                        .findFirst()
                        .orElse(LoadLocators.class.getName()));
        Map<String, String> pageLocators = PER_CLASS.get(caller);
        if (pageLocators == null) {
            throw new IllegalStateException(
                    "No locators loaded for " + caller + ". Add a static block:\n"
                            + "    static { loadLocators(\"<PageName>\"); }");
        }
        return get(pageLocators, key);
    }

    /**
     * Reads the CSV belonging to {@code page} and hands the map back to the caller.
     *
     * <p>Prefer {@link #loadLocators(String)} from a static block; this form exists
     * for callers that want the map directly.
     *
     * <p>The result is parsed once per JVM and cached under the page name.
     *
     * @param page the CSV base name, e.g. {@code "Login"}
     * @throws IllegalStateException when the CSV is absent, empty or malformed
     */
    public static Map<String, String> ctors(String page) {
        if (isBlank(page)) {
            throw new IllegalStateException("Locator page name must not be empty");
        }
        String fileName = FILE_PREFIX + page.trim() + FILE_SUFFIX;
        return CACHE.computeIfAbsent(fileName, ignored -> read(fileName));
    }

    /**
     * Looks up one selector in an explicit page map.
     *
     * @throws IllegalStateException when the key is absent, so a typo surfaces
     *                               immediately instead of as a Playwright timeout
     */
    protected static String select(Map<String, String> pageLocators, String key) {
        return get(pageLocators, key);
    }

    /**
     * Looks up one selector from an already-loaded page map.
     *
     * <pre>
     *   String selector = LoadLocators.get(locators, "usernameField");
     * </pre>
     *
     * @throws IllegalStateException when the key is absent, so a typo surfaces
     *                               immediately instead of as a Playwright timeout
     */
    public static String get(Map<String, String> pageLocators, String key) {
        if (pageLocators == null) {
            throw new IllegalStateException(
                    "Locator map is null - was LoadLocators.ctors(...) run in the "
                            + "static initializer of this page class?");
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

    /** Resolves and parses one CSV, preferring the classpath over the filesystem. */
    private static Map<String, String> read(String fileName) {
        String folder = folderPath();
        Map<String, String> entries = new LinkedHashMap<>();

        String resource = folder.isEmpty() ? fileName : folder + "/" + fileName;
        if (!resource.startsWith("/")) {
            resource = "/" + resource;
        }

        try (InputStream in = LoadLocators.class.getResourceAsStream(resource)) {
            if (in != null) {
                parse(in, resource, entries);
                return entries;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read locator file '" + resource + "'", e);
        }

        Path path = Paths.get(folder, fileName);
        if (!Files.isReadable(path)) {
            throw new IllegalStateException(
                    "Locator file '" + fileName + "' not found on the classpath as '"
                            + resource + "' and not readable on disk at '"
                            + path.toAbsolutePath() + "'. Expected it in src/main/java/"
                            + "com/satviklabs/customLocators/ and copied by pom.xml "
                            + "<testResources>.");
        }
        try (InputStream in = Files.newInputStream(path)) {
            parse(in, path.toString(), entries);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read locator file '" + path + "'", e);
        }
        return entries;
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