package com.uitesttools.demo.pages

import android.content.Intent
import androidx.test.uiautomator.UiDevice
import com.uitesttools.uitest.pageobject.Orientation
import com.uitesttools.uitest.pageobject.PageManager

/**
 * Demo Page Manager / Pages aggregator — the single entry point a demo test
 * talks to.
 *
 * Subclasses the reusable [PageManager] (UIAutomator, cross-process default),
 * pins orientation before launch, and exposes the app's Page Objects as lazy
 * accessors so a test reads `pm.mainPage.tapIncrement()` and never touches
 * `UiDevice` / `By` directly.
 *
 * Test-directed IoC (when needed) goes through [configureIntent] with the shared
 * `TestArgs` keys and `com.uitesttools.uitest.testargs.putTestArgs`, e.g.
 * `DemoPages(device) { putTestArgs(strings = mapOf(TestArgs.SEED_DATASET to "5")) }`.
 */
class DemoPages(
    device: UiDevice,
    orientation: Orientation = Orientation.PORTRAIT,
    autoLaunch: Boolean = true,
    configureIntent: Intent.() -> Unit = {},
) : PageManager(
    device = device,
    packageName = PACKAGE_NAME,
    orientation = orientation,
    configureIntent = configureIntent,
    autoLaunch = autoLaunch,
) {

    /** The demo counter screen. */
    val mainPage: MainPage by lazy { MainPage(device) }

    companion object {
        const val PACKAGE_NAME = "com.uitesttools.demo"
    }
}
