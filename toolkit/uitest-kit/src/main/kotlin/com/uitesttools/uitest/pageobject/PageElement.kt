package com.uitesttools.uitest.pageobject

import androidx.test.espresso.ViewInteraction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2

/**
 * Protocol for Page Object pattern elements.
 *
 * A PageElement represents a screen or major UI section that can:
 * - Wait for itself to be ready (loaded)
 * - Provide access to child elements
 * - Define navigation actions
 *
 * Usage:
 * ```kotlin
 * class LoginPage(
 *     override val device: UiDevice
 * ) : PageElement {
 *
 *     override val readyMarker: String = "Auth_Login_Title_text"
 *
 *     val usernameField: UiObject2?
 *         get() = device.findObject(By.res(testTag("Auth_Login_Username_input")))
 *
 *     val passwordField: UiObject2?
 *         get() = device.findObject(By.res(testTag("Auth_Login_Password_input")))
 *
 *     val loginButton: UiObject2?
 *         get() = device.findObject(By.res(testTag("Auth_Login_Submit_button")))
 *
 *     fun login(username: String, password: String): HomePage {
 *         usernameField?.text = username
 *         passwordField?.text = password
 *         loginButton?.click()
 *         return HomePage(device).waitForReady()
 *     }
 * }
 * ```
 */
interface PageElement {

    /**
     * The UiDevice instance for interacting with the UI.
     */
    val device: UiDevice

    /**
     * Test tag/resource ID that indicates this page is ready.
     * Used by waitForReady() to determine when the page is loaded.
     */
    val readyMarker: String

    /**
     * Default timeout for waiting operations (milliseconds).
     */
    val defaultTimeout: Long
        get() = 5000L

    /**
     * Wait for this page to be ready.
     *
     * @param timeout Maximum time to wait in milliseconds
     * @return This page element, or throws if timeout
     * @throws AssertionError if page doesn't become ready within timeout
     */
    fun waitForReady(timeout: Long = defaultTimeout): PageElement {
        val found = device.wait(
            androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.res(testTag(readyMarker))
            ),
            timeout
        )

        if (!found) {
            throw AssertionError(
                "Page '${this::class.simpleName}' did not become ready within ${timeout}ms. " +
                "Expected element with tag '$readyMarker'"
            )
        }

        return this
    }

    /**
     * Check if this page is currently displayed.
     */
    fun isDisplayed(): Boolean {
        return device.hasObject(
            androidx.test.uiautomator.By.res(testTag(readyMarker))
        )
    }

    /**
     * Build the full resource ID from a test tag.
     *
     * Override this if your app uses a different package name pattern.
     * Default assumes the test tag is used directly as a resource ID suffix.
     */
    fun testTag(tag: String): String {
        // UIAutomator expects full resource ID: "package:id/identifier"
        // For Compose testTag, it's typically just the tag value
        // This can be overridden per project
        return tag
    }
}

/**
 * Convenience function to create a test tag pattern.
 *
 * BEM-like naming: {Module}_{Screen}_{Element}_{Action}
 *
 * Examples:
 * - "Auth_Login_Username_input"
 * - "Settings_Profile_Avatar_button"
 * - "Home_Feed_Post_card"
 */
fun buildTestTag(
    module: String,
    screen: String,
    element: String,
    type: String
): String = "${module}_${screen}_${element}_${type}"
