package com.uitesttools.demo.testids

/**
 * Shared test constants — timeouts and test credentials.
 *
 * Direct port of iOS `Consts.Timeouts` / `TestCredentials`, expressed as plain
 * Kotlin values (millis as `Long`) so this module stays Android-dependency-free.
 */
object TestConsts {

    /** Wait budgets in milliseconds, for `waitForResourceId(tag, timeout)` etc. */
    object Timeouts {
        const val SHORT_MS = 2_000L
        const val DEFAULT_MS = 5_000L
        const val LONG_MS = 15_000L
        const val NETWORK_MS = 30_000L
    }

    /** Deterministic credentials used by test fixtures — never real secrets. */
    object TestCredentials {
        const val USERNAME = "test_user"
        const val PASSWORD = "test_password_123"
        const val EMAIL = "test_user@example.com"
    }
}
