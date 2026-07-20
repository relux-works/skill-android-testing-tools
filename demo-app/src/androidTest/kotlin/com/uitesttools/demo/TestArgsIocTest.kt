package com.uitesttools.demo

import com.uitesttools.demo.pages.MainPage
import com.uitesttools.demo.testids.TestArgs
import com.uitesttools.uitest.pageobject.BaseUiTestSuite
import com.uitesttools.uitest.testargs.putTestArgs
import org.junit.Test

/**
 * Instrumented example for the intent-extras test-directed IoC convention
 * (design §7 F). Demonstrates the Android divergence from iOS: a flag flips app
 * state via **intent extras on the launch intent**, NOT via instrumentation
 * `-e` args (which never reach the app process) or `ProcessInfo`.
 *
 * Requires a connected device/emulator — this is the reference for the pattern;
 * the pure launch/read logic is covered by fast JVM unit tests in
 * `uitest-kit` (`TestArgsReaderTest` / `TestArgsLaunchTest`).
 */
class TestArgsIocTest : BaseUiTestSuite() {

    override val packageName = "com.uitesttools.demo"

    @Test
    fun defaultLaunchStartsAtZero() {
        launchApp()
        val page = MainPage(device).waitForReady()
        screenshot(1, "default_launch")
        page.assertCounterEquals(0)
    }

    @Test
    fun seedDatasetExtraFlipsInitialState() {
        // The shared TestArgs key is delivered as a String intent extra; the app
        // reads it off its launch intent and seeds the counter.
        launchApp {
            putTestArgs(strings = mapOf(TestArgs.SEED_DATASET to "42"))
        }
        val page = MainPage(device).waitForReady()
        screenshot(1, "seeded_via_intent_extra")
        page.assertCounterEquals(42)
    }
}
