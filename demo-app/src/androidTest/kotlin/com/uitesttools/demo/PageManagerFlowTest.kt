package com.uitesttools.demo

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.uitesttools.demo.pages.DemoPages
import com.uitesttools.demo.testids.TestArgs
import com.uitesttools.screenshot.ScreenshotSessionRule
import com.uitesttools.uitest.pageobject.Orientation
import com.uitesttools.uitest.testargs.putTestArgs
import org.junit.ClassRule
import org.junit.Test

/**
 * Demonstrates the PageManager / Pages aggregator: a test constructs [DemoPages],
 * which pins orientation and launches the app, then drives the flow purely
 * through the manager (`pm.mainPage.*`) with the manager's `screenshot()`
 * passthrough — the test never touches `UiDevice` after building the manager.
 *
 * Contrast with [CounterTest], which builds pages inline via
 * [com.uitesttools.uitest.pageobject.BaseUiTestSuite].
 */
class PageManagerFlowTest {

    companion object {
        @get:ClassRule
        @JvmStatic
        val sessionRule = ScreenshotSessionRule()
    }

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun counterFlowThroughManager() {
        val pm = DemoPages(device)              // pins portrait, launches app
        pm.screenshot(1, "app_launched")

        pm.mainPage.waitForReady()
            .assertCounterEquals(0)
            .tapIncrement()
            .tapIncrement()
        pm.screenshot(2, "after_two_increments")
        pm.mainPage.assertCounterEquals(2)

        pm.mainPage.tapReset().assertCounterEquals(0)
        pm.screenshot(3, "after_reset")
    }

    @Test
    fun relaunchWithSeededArgsAndOrientation() {
        // Test-directed IoC: shared TestArgs keys → intent extras (the app reads
        // SEED_DATASET off its launch intent and starts the counter seeded).
        val seed: Intent.() -> Unit = { putTestArgs(strings = mapOf(TestArgs.SEED_DATASET to "5")) }

        val pm = DemoPages(device, orientation = Orientation.PORTRAIT, configureIntent = seed)
        pm.mainPage.waitForReady().assertCounterEquals(5)
        pm.screenshot(1, "launched_seeded")

        pm.relaunch(orientation = Orientation.PORTRAIT, extraConfig = seed)
        pm.mainPage.waitForReady().assertCounterEquals(5)
        pm.screenshot(2, "after_relaunch")

        pm.terminate()
    }
}
