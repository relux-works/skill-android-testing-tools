package com.uitesttools.uitest.extensions

import android.view.View
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import org.hamcrest.Matcher
import org.hamcrest.Matchers

/**
 * Extension functions for Espresso ViewInteraction.
 *
 * Provides convenience methods for common UI test operations.
 */

/**
 * Wait for a view property to have a specific value.
 *
 * @param property Function to extract the property value
 * @param expectedValue The value to wait for
 * @param timeout Maximum time to wait in milliseconds
 * @param pollInterval Time between checks in milliseconds
 * @return true if the property reached the expected value, false on timeout
 *
 * Usage:
 * ```kotlin
 * onView(withId(R.id.button))
 *     .waitFor({ it.isEnabled }, toBe = true, timeout = 5000)
 * ```
 */
fun <T> ViewInteraction.waitFor(
    property: (View) -> T,
    toBe: T,
    timeout: Long = 5000,
    pollInterval: Long = 100
): Boolean {
    val endTime = System.currentTimeMillis() + timeout

    while (System.currentTimeMillis() < endTime) {
        try {
            var currentValue: T? = null

            perform(object : ViewAction {
                override fun getConstraints(): Matcher<View> = Matchers.any(View::class.java)
                override fun getDescription(): String = "get property value"
                override fun perform(uiController: UiController, view: View) {
                    currentValue = property(view)
                }
            })

            if (currentValue == toBe) {
                return true
            }
        } catch (e: Exception) {
            // View not found or property access failed, continue waiting
        }

        Thread.sleep(pollInterval)
    }

    return false
}

/**
 * Wait for a view to match a given matcher.
 *
 * @param matcher The matcher to check
 * @param timeout Maximum time to wait
 * @return true if view matched, false on timeout
 */
fun ViewInteraction.waitUntil(
    matcher: Matcher<View>,
    timeout: Long = 5000,
    pollInterval: Long = 100
): Boolean {
    val endTime = System.currentTimeMillis() + timeout

    while (System.currentTimeMillis() < endTime) {
        try {
            check(ViewAssertions.matches(matcher))
            return true
        } catch (e: Exception) {
            // Not matched yet
        }

        Thread.sleep(pollInterval)
    }

    return false
}

/**
 * Wait for a view to be visible.
 */
fun ViewInteraction.waitUntilVisible(timeout: Long = 5000): Boolean {
    return waitUntil(ViewMatchers.isDisplayed(), timeout)
}

/**
 * Wait for a view to be gone or invisible.
 */
fun ViewInteraction.waitUntilGone(timeout: Long = 5000): Boolean {
    return waitUntil(
        Matchers.not(ViewMatchers.isDisplayed()),
        timeout
    )
}

/**
 * Wait for a view to be enabled.
 */
fun ViewInteraction.waitUntilEnabled(timeout: Long = 5000): Boolean {
    return waitUntil(ViewMatchers.isEnabled(), timeout)
}

/**
 * Click at a specific offset within the view.
 *
 * Useful for clicking specific parts of a view (like a Switch toggle).
 *
 * @param dx Horizontal offset (0.0 = left, 0.5 = center, 1.0 = right)
 * @param dy Vertical offset (0.0 = top, 0.5 = center, 1.0 = bottom)
 *
 * Usage:
 * ```kotlin
 * // Click at the right side of a Switch (the toggle part)
 * onView(withId(R.id.switch)).clickAtOffset(0.9, 0.5)
 * ```
 */
fun ViewInteraction.clickAtOffset(dx: Float, dy: Float): ViewInteraction {
    return perform(
        GeneralClickAction(
            Tap.SINGLE,
            { view ->
                val screenPos = IntArray(2)
                view.getLocationOnScreen(screenPos)

                val x = screenPos[0] + (view.width * dx)
                val y = screenPos[1] + (view.height * dy)

                floatArrayOf(x, y)
            },
            Press.FINGER,
            0, // inputDevice
            0  // buttonState
        )
    )
}

/**
 * Scroll to this view and then click.
 */
fun ViewInteraction.scrollToAndClick(): ViewInteraction {
    return perform(ViewActions.scrollTo(), ViewActions.click())
}

/**
 * Type text and then close the keyboard.
 */
fun ViewInteraction.typeTextAndCloseKeyboard(text: String): ViewInteraction {
    return perform(ViewActions.typeText(text), ViewActions.closeSoftKeyboard())
}

/**
 * Clear text field and type new text.
 */
fun ViewInteraction.replaceTextAndCloseKeyboard(text: String): ViewInteraction {
    return perform(ViewActions.clearText(), ViewActions.typeText(text), ViewActions.closeSoftKeyboard())
}

/**
 * Long click on the view.
 */
fun ViewInteraction.longClick(): ViewInteraction {
    return perform(ViewActions.longClick())
}

/**
 * Double click on the view.
 */
fun ViewInteraction.doubleClick(): ViewInteraction {
    return perform(ViewActions.doubleClick())
}

/**
 * Swipe up on the view.
 */
fun ViewInteraction.swipeUp(): ViewInteraction {
    return perform(ViewActions.swipeUp())
}

/**
 * Swipe down on the view.
 */
fun ViewInteraction.swipeDown(): ViewInteraction {
    return perform(ViewActions.swipeDown())
}

/**
 * Swipe left on the view.
 */
fun ViewInteraction.swipeLeft(): ViewInteraction {
    return perform(ViewActions.swipeLeft())
}

/**
 * Swipe right on the view.
 */
fun ViewInteraction.swipeRight(): ViewInteraction {
    return perform(ViewActions.swipeRight())
}
