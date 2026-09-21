package com.satviklabs.support;

import com.microsoft.playwright.Playwright;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the single Playwright driver instance for the whole test run.
 *
 * Playwright's Java driver is an expensive process to start and is safe to
 * share across threads (each scenario gets its own Browser/Context/Page).
 *
 * It is deliberately NOT a ThreadLocal: a ThreadLocal value cannot be reliably
 * closed from a suite-level teardown, because that hook runs on a different
 * thread and would close it while other threads still need it.
 */
public final class PlaywrightProvider {

    private static final AtomicReference<Playwright> INSTANCE = new AtomicReference<>();

    private PlaywrightProvider() {
    }

    public static Playwright get() {
        Playwright existing = INSTANCE.get();
        if (existing != null) {
            return existing;
        }
        synchronized (PlaywrightProvider.class) {
            Playwright current = INSTANCE.get();
            if (current == null) {
                current = Playwright.create();
                INSTANCE.set(current);
            }
            return current;
        }
    }

    /** Closes the driver once, at the very end of the run. Idempotent. */
    public static synchronized void close() {
        Playwright instance = INSTANCE.getAndSet(null);
        if (instance != null) {
            try {
                instance.close();
            } catch (Exception ignored) {
                // The driver is being shut down anyway; nothing useful to do.
            }
        }
    }
}
