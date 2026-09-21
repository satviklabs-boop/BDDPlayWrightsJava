package com.satviklabs.config;

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

    public static String apiBaseUrl() {
        return get("API_BASE_URL", "https://reqres.in");
    }

    /** Value for the {@code x-api-key} header. Reqres now requires a key. */
    public static String apiKey() {
        return get("API_KEY", "reqres-free-v1");
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

    public static String env() {
        return get("ENV", "dev");
    }

    /** Directory that failure screenshots/traces are written to. */
    public static String artifactDir() {
        return get("ARTIFACT_DIR", "target/artifacts");
    }
}
