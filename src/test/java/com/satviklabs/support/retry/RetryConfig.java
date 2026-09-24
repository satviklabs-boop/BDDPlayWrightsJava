package com.satviklabs.support.retry;

import com.satviklabs.config.ConfigLoader;

/**
 * Configuration for the retry analyser.
 *
 * Every value resolves through {@link ConfigLoader}, so it can be overridden on
 * the command line (-DRETRY_MAX_ATTEMPTS=2), in the environment, or in .env /
 * config.properties exactly like every other setting.
 *
 *  RETRY_ENABLED        (default true)      master on/off switch
 *  RETRY_MAX_ATTEMPTS   (default 1)         extra runs per failed scenario
 *  RETRY_TAG_EXCLUDE    (default @no-retry) scenarios that must never retry
 *  RETRY_REPORT_DIR     (default target/retry) where the ledger is written
 */
public final class RetryConfig {

    private RetryConfig() {
    }

    /** Whether failed scenarios may be retried at all. */
    public static boolean enabled() {
        return ConfigLoader.getBoolean("RETRY_ENABLED", true);
    }

    /**
     * Number of EXTRA attempts per failed scenario. 1 means "run it once more",
     * 2 means "up to two more times". 0 disables retrying entirely.
     */
    public static int maxAttempts() {
        int value = ConfigLoader.getInt("RETRY_MAX_ATTEMPTS", 1);
        return Math.max(0, value);
    }

    /**
     * Scenarios carrying this tag are never retried. Useful for deliberately
     * negative tests and for checks whose first failure is already meaningful.
     */
    public static String excludeTag() {
        return ConfigLoader.getString("RETRY_TAG_EXCLUDE", "@no-retry");
    }

    /** Directory holding the retry ledger and the generated retry report. */
    public static String reportDir() {
        return ConfigLoader.getString("RETRY_REPORT_DIR", "target/retry");
    }

    /** Convenience: are retries actually in play for this run? */
    public static boolean active() {
        return enabled() && maxAttempts() > 0;
    }
}
