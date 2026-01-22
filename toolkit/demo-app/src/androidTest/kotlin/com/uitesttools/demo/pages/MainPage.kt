package com.uitesttools.demo.pages

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import com.uitesttools.demo.TestTags
import com.uitesttools.uitest.pageobject.PageElement

/**
 * Page Object for the Main screen.
 */
class MainPage(override val device: UiDevice) : PageElement {

    override val readyMarker = TestTags.Main.TITLE

    override fun waitForReady(timeout: Long): MainPage {
        super.waitForReady(timeout)
        return this
    }

    // ----- Elements -----

    val title: UiObject2?
        get() = device.findObject(By.res(TestTags.Main.TITLE))

    val counter: UiObject2?
        get() = device.findObject(By.res(TestTags.Main.COUNTER))

    val incrementButton: UiObject2?
        get() = device.findObject(By.res(TestTags.Main.INCREMENT_BUTTON))

    val decrementButton: UiObject2?
        get() = device.findObject(By.res(TestTags.Main.DECREMENT_BUTTON))

    val resetButton: UiObject2?
        get() = device.findObject(By.res(TestTags.Main.RESET_BUTTON))

    // ----- Actions -----

    fun getCounterValue(): Int {
        return counter?.text?.toIntOrNull() ?: 0
    }

    fun tapIncrement(): MainPage {
        incrementButton?.click()
        device.waitForIdle()
        return this
    }

    fun tapDecrement(): MainPage {
        decrementButton?.click()
        device.waitForIdle()
        return this
    }

    fun tapReset(): MainPage {
        resetButton?.click()
        device.waitForIdle()
        return this
    }

    fun increment(times: Int): MainPage {
        repeat(times) { tapIncrement() }
        return this
    }

    fun decrement(times: Int): MainPage {
        repeat(times) { tapDecrement() }
        return this
    }

    // ----- Assertions -----

    fun assertCounterEquals(expected: Int): MainPage {
        val actual = getCounterValue()
        require(actual == expected) {
            "Expected counter to be $expected, but was $actual"
        }
        return this
    }
}
