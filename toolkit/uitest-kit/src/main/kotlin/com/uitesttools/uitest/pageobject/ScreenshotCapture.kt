package com.uitesttools.uitest.pageobject

import com.uitesttools.screenshot.ScreenshotManager

/**
 * The screenshot sink [PageManager] delegates to for its `screenshot()`
 * passthrough.
 *
 * The passthrough exists so a test only ever talks to the manager
 * (`pm.screenshot(1, "login")`) instead of reaching into a base suite or
 * `ScreenshotManager` directly — the "tests only talk to the manager" contract
 * iOS's `PageManager` has.
 *
 * It is an injectable seam (default [Default]) so the delegation is unit-testable
 * without a device and so a consumer can route captures elsewhere (e.g. an
 * in-process Compose bitmap) if needed.
 */
interface ScreenshotCapture {

    /** Capture a screenshot with an explicit [step] number. */
    fun capture(step: Int, description: String)

    /** Capture a screenshot with an auto-incrementing step number. */
    fun capture(description: String)

    companion object {
        /**
         * Default sink: the structured-naming [ScreenshotManager] used across the
         * toolkit (`Run_{session}__Test_{name}__Step_{NN}__…`), captured via
         * UIAutomator so it works for the cross-process default lane.
         */
        val Default: ScreenshotCapture = object : ScreenshotCapture {
            override fun capture(step: Int, description: String) {
                ScreenshotManager.screenshot(step, description)
            }

            override fun capture(description: String) {
                ScreenshotManager.screenshot(description)
            }
        }
    }
}
