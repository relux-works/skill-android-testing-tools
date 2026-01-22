package com.uitesttools.uitest.extensions

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * Extension functions for UIAutomator UiDevice.
 *
 * Provides convenience methods for common UI test operations.
 */

/**
 * Wait for an element with a specific resource ID to appear.
 *
 * @param resourceId The resource ID to wait for
 * @param timeout Maximum time to wait in milliseconds
 * @return The found element, or null if timeout
 */
fun UiDevice.waitForResourceId(
    resourceId: String,
    timeout: Long = 5000
): UiObject2? {
    wait(Until.hasObject(By.res(resourceId)), timeout)
    return findObject(By.res(resourceId))
}

/**
 * Wait for an element with specific text to appear.
 *
 * @param text The text to wait for
 * @param timeout Maximum time to wait
 * @return The found element, or null if timeout
 */
fun UiDevice.waitForText(
    text: String,
    timeout: Long = 5000
): UiObject2? {
    wait(Until.hasObject(By.text(text)), timeout)
    return findObject(By.text(text))
}

/**
 * Wait for an element with specific text (containing) to appear.
 */
fun UiDevice.waitForTextContains(
    text: String,
    timeout: Long = 5000
): UiObject2? {
    wait(Until.hasObject(By.textContains(text)), timeout)
    return findObject(By.textContains(text))
}

/**
 * Wait for an element with specific content description to appear.
 */
fun UiDevice.waitForDescription(
    description: String,
    timeout: Long = 5000
): UiObject2? {
    wait(Until.hasObject(By.desc(description)), timeout)
    return findObject(By.desc(description))
}

/**
 * Wait for any element matching the selector to appear.
 */
fun UiDevice.waitFor(
    selector: BySelector,
    timeout: Long = 5000
): UiObject2? {
    wait(Until.hasObject(selector), timeout)
    return findObject(selector)
}

/**
 * Wait for an element to disappear.
 *
 * @param selector The selector to check
 * @param timeout Maximum time to wait
 * @return true if element disappeared, false if still present after timeout
 */
fun UiDevice.waitUntilGone(
    selector: BySelector,
    timeout: Long = 5000
): Boolean {
    return wait(Until.gone(selector), timeout) ?: false
}

/**
 * Wait for an element with resource ID to disappear.
 */
fun UiDevice.waitUntilResourceIdGone(
    resourceId: String,
    timeout: Long = 5000
): Boolean {
    return waitUntilGone(By.res(resourceId), timeout)
}

/**
 * Wait for text to disappear.
 */
fun UiDevice.waitUntilTextGone(
    text: String,
    timeout: Long = 5000
): Boolean {
    return waitUntilGone(By.text(text), timeout)
}

/**
 * Check if an element with resource ID exists.
 */
fun UiDevice.hasResourceId(resourceId: String): Boolean {
    return hasObject(By.res(resourceId))
}

/**
 * Check if an element with text exists.
 */
fun UiDevice.hasText(text: String): Boolean {
    return hasObject(By.text(text))
}

/**
 * Check if an element with content description exists.
 */
fun UiDevice.hasDescription(description: String): Boolean {
    return hasObject(By.desc(description))
}

/**
 * Find an element by test tag (Compose) or resource ID.
 */
fun UiDevice.findByTag(tag: String): UiObject2? {
    return findObject(By.res(tag))
}

/**
 * Find all elements by test tag.
 */
fun UiDevice.findAllByTag(tag: String): List<UiObject2> {
    return findObjects(By.res(tag))
}

/**
 * Click on an element with resource ID.
 * @return true if element was found and clicked
 */
fun UiDevice.clickResourceId(resourceId: String): Boolean {
    return findObject(By.res(resourceId))?.also { it.click() } != null
}

/**
 * Click on an element with text.
 * @return true if element was found and clicked
 */
fun UiDevice.clickText(text: String): Boolean {
    return findObject(By.text(text))?.also { it.click() } != null
}

/**
 * Type text into an element with resource ID.
 * @return true if element was found and text was entered
 */
fun UiDevice.typeIntoResourceId(resourceId: String, text: String): Boolean {
    return findObject(By.res(resourceId))?.also { it.text = text } != null
}

/**
 * Clear and type text into an element.
 */
fun UiDevice.clearAndTypeInto(resourceId: String, text: String): Boolean {
    return findObject(By.res(resourceId))?.also {
        it.clear()
        it.text = text
    } != null
}

/**
 * Scroll down until an element appears or max scrolls reached.
 *
 * @param selector Element to find
 * @param maxScrolls Maximum number of scroll attempts
 * @return The found element, or null
 */
fun UiDevice.scrollDownUntilFound(
    selector: BySelector,
    maxScrolls: Int = 10
): UiObject2? {
    repeat(maxScrolls) {
        findObject(selector)?.let { return it }

        // Scroll down
        swipe(
            displayWidth / 2,
            displayHeight * 3 / 4,
            displayWidth / 2,
            displayHeight / 4,
            20
        )

        // Wait for scroll to complete
        waitForIdle(500)
    }

    return findObject(selector)
}

/**
 * Scroll up until an element appears or max scrolls reached.
 */
fun UiDevice.scrollUpUntilFound(
    selector: BySelector,
    maxScrolls: Int = 10
): UiObject2? {
    repeat(maxScrolls) {
        findObject(selector)?.let { return it }

        // Scroll up
        swipe(
            displayWidth / 2,
            displayHeight / 4,
            displayWidth / 2,
            displayHeight * 3 / 4,
            20
        )

        waitForIdle(500)
    }

    return findObject(selector)
}

/**
 * Wait for the app's UI to be idle.
 *
 * @param timeout Idle timeout in milliseconds
 */
fun UiDevice.waitForUiIdle(timeout: Long = 5000) {
    waitForIdle(timeout)
}

/**
 * Dismiss system dialogs (like permission dialogs) by pressing back.
 */
fun UiDevice.dismissSystemDialogs() {
    // Try pressing back to dismiss any system UI
    pressBack()
    waitForIdle(500)
}

/**
 * Grant a runtime permission via shell command.
 *
 * @param packageName App package
 * @param permission Full permission string (e.g., "android.permission.CAMERA")
 */
fun UiDevice.grantPermission(packageName: String, permission: String) {
    executeShellCommand("pm grant $packageName $permission")
}

/**
 * Revoke a runtime permission.
 */
fun UiDevice.revokePermission(packageName: String, permission: String) {
    executeShellCommand("pm revoke $packageName $permission")
}

/**
 * Enable/disable animations (useful for faster tests).
 *
 * Note: Requires adb shell permissions.
 */
fun UiDevice.setAnimationsEnabled(enabled: Boolean) {
    val scale = if (enabled) "1" else "0"
    executeShellCommand("settings put global window_animation_scale $scale")
    executeShellCommand("settings put global transition_animation_scale $scale")
    executeShellCommand("settings put global animator_duration_scale $scale")
}
