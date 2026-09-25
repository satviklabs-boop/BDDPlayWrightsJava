package com.satviklabs.commonUtils;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Central configuration loader.
 *
 * Resolution order (first match wins):
 *   1. JVM system property   (-DBASE_URL=...)
 *   2. Environment variable  (BASE_URL=... or a .env file)
 *   3. src/test/resources/config.properties
 *   4. Built-in default
 *
 * Keeping every setting here means no test or page object ever hard-codes a URL.
 */
public final class ConfigLoader {

    private static final Properties FILE_PROPS = new Properties();
    private static final Dotenv DOTENV = loadDotenv();

    static {
        try (InputStream in = ConfigLoader.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (in != null) {
                FILE_PROPS.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read config.properties", e);
        }
    }

    private ConfigLoader() {
    }

    /** Reads .env from the project root if present; never fails when absent. */
    private static Dotenv loadDotenv() {
        try {
            return Dotenv.configure()
                    .ignoreIfMissing()
                    .ignoreIfMalformed()
                    .load();
        } catch (Exception e) {
            return null;
        }
    }

    private static String get(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (isNotBlank(value)) {
            return value.trim();
        }
        value = System.getenv(key);
        if (isNotBlank(value)) {
            return value.trim();
        }
        if (DOTENV != null) {
            value = DOTENV.get(key);
            if (isNotBlank(value)) {
                return value.trim();
            }
        }
        value = FILE_PROPS.getProperty(key);
        if (isNotBlank(value)) {
            return value.trim();
        }
        return defaultValue;
    }

    /** Typed accessors so callers never parse raw strings themselves. */
    public static String getString(String key, String defaultValue) {
        return get(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        String value = get(key, null);
        if (!isNotBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key, null);
        if (!isNotBlank(value)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    // ---------------------------------------------------------------- UI

    public static String uiBaseUrl() {
        return get("BASE_URL", "https://the-internet.herokuapp.com");
    }

    public static String uiUsername() {
        return get("UI_USERNAME", "tomsmith");
    }

    public static String uiPassword() {
        return get("UI_PASSWORD", "SuperSecretPassword!");
    }

    // --------------------------------------------------------------- API

    /**
     * Default is jsonplaceholder: it needs no API key and has no daily quota,
     * so the suite is green out of the box.
     *
     * reqres.in is a richer demo API but caps anonymous use at 40 requests/day
     * per IP, which breaks a full run once exhausted. To use it instead, set
     * API_BASE_URL=https://reqres.in and supply a working API_KEY.
     */
    public static String apiBaseUrl() {
        return get("API_BASE_URL", "https://jsonplaceholder.typicode.com");
    }

    /** Optional. Only required by targets that authenticate, such as reqres.in. */
    public static String apiKey() {
        return get("API_KEY", "");
    }

    public static String apiUsername() {
        return get("API_USERNAME", "eve.holt@reqres.in");
    }

    public static String apiPassword() {
        return get("API_PASSWORD", "cityslicka");
    }

    // ---------------------------------------------------------- execution

    public static boolean headless() {
        return Boolean.parseBoolean(get("HEADLESS", "true"));
    }

    public static int slowMo() {
        return Integer.parseInt(get("SLOW_MO", "0"));
    }

    public static int defaultTimeoutMs() {
        return Integer.parseInt(get("DEFAULT_TIMEOUT", "30000"));
    }

    /**
     * Per-request HTTP timeout. Deliberately larger than the UI default: public
     * demo APIs can be slow, and a slow response is not a test defect.
     */
    public static int apiTimeoutMs() {
        return Integer.parseInt(get("API_TIMEOUT", "60000"));
    }

    public static String env() {
        return get("ENV", "dev");
    }

    /** Directory that failure screenshots/traces are written to. */
    public static String artifactDir() {
        return get("ARTIFACT_DIR", "target/artifacts");
    }

    // ----------------------------------------------------------- locators

    /**
     * Folder holding the per-page locator CSVs, relative to the classpath root.
     *
     * <p>Default points at the package of the locator classes. Override to read a
     * different set of selector files without a rebuild:
     *
     * <pre>
     *   -DlocatorsFolderPath=com/satviklabs/customLocators
     *   locatorsFolderPath=/absolute/path/to/csv
     * </pre>
     */
    public static String locatorsFolderPath() {
        return get("locatorsFolderPath", "locators");
    }
}
