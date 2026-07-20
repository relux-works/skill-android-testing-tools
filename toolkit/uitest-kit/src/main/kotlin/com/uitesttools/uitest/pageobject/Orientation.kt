package com.uitesttools.uitest.pageobject

import androidx.test.uiautomator.UiDevice

/**
 * Device orientation, forced by [PageManager] **before** launch so extracted
 * screenshots are never captured in an unexpected rotation.
 *
 * A rotated frame is the #1 screenshot-review failure (see the mandatory
 * visual-verify gate in `SKILL.md`); pinning orientation up front removes it.
 */
enum class Orientation {
    /** Natural portrait (rotation frozen). */
    PORTRAIT,

    /** Landscape (rotation frozen). */
    LANDSCAPE,

    /** The device's natural orientation, whatever that is (rotation frozen). */
    NATURAL,
}

/**
 * Force [orientation] on this device, freezing auto-rotation.
 *
 * Wraps UIAutomator's three `setOrientation*` calls behind the [Orientation]
 * enum so the aggregator centralizes "pin orientation before launch".
 */
fun UiDevice.setOrientation(orientation: Orientation) {
    when (orientation) {
        Orientation.PORTRAIT -> setOrientationPortrait()
        Orientation.LANDSCAPE -> setOrientationLandscape()
        Orientation.NATURAL -> setOrientationNatural()
    }
}
