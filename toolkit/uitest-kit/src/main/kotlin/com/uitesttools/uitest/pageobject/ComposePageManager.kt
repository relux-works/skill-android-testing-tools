package com.uitesttools.uitest.pageobject

import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * The **in-process** aggregator: the second PageManager mode, for pure-Compose
 * UI tests that drive the composition directly through a [ComposeTestRule]
 * instead of launching the app cross-process.
 *
 * [PageManager] is the default (UIAutomator, separate process, works on physical
 * devices and the screenshot lanes). Reach for [ComposePageManager] only when a
 * test is genuinely in-process — a `createComposeRule()` /
 * `createAndroidComposeRule<Activity>()` test of Compose content — where
 * `onNodeWithTag` is faster and more precise than `By.res` and no app launch is
 * needed.
 *
 * It plays the same aggregator role: hold the one rule, expose lazy Compose
 * pages, and offer the `screenshot()` passthrough so a test still only talks to
 * the manager.
 *
 * ```kotlin
 * class MainComposeTest {
 *     @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
 *
 *     private val pm by lazy { AppComposePages(composeRule) }
 *
 *     @Test fun increments() {
 *         pm.screenshot(1, "launched")
 *         pm.mainPage.tapIncrement().assertCounter(1)
 *         pm.screenshot(2, "incremented")
 *     }
 * }
 *
 * class AppComposePages(rule: ComposeTestRule) : ComposePageManager(rule) {
 *     val mainPage by lazy { MainComposePage(rule) }
 * }
 * ```
 *
 * Note on Compose + UIAutomator: if a test mixes the two, the Compose root must
 * set `Modifier.semantics { testTagsAsResourceId = true }` or UIAutomator's
 * `By.res(tag)` finds nothing. The in-process [ComposeTestRule] path
 * (`onNodeWithTag`) does not need that flag.
 *
 * @param composeRule the in-process Compose test rule every page queries through.
 * @param screenshotCapture sink for the [screenshot] passthrough; the default
 *   captures the whole device via `ScreenshotManager`.
 */
open class ComposePageManager(
    val composeRule: ComposeTestRule,
    private val screenshotCapture: ScreenshotCapture = ScreenshotCapture.Default,
) {

    /**
     * Screenshot passthrough — keeps the "tests only talk to the manager"
     * contract in the in-process mode too.
     */
    fun screenshot(step: Int, description: String) =
        screenshotCapture.capture(step, description)

    /** Screenshot passthrough with an auto-incrementing step number. */
    fun screenshot(description: String) = screenshotCapture.capture(description)
}
