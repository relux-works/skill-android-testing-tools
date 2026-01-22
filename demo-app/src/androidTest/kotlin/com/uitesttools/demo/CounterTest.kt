package com.uitesttools.demo

import com.uitesttools.demo.pages.MainPage
import com.uitesttools.uitest.pageobject.BaseUiTestSuite
import org.junit.Test

/**
 * UI tests for the counter functionality.
 *
 * Uses Page Object pattern and screenshot capture.
 */
class CounterTest : BaseUiTestSuite() {

    override val packageName = "com.uitesttools.demo"

    @Test
    fun testInitialState() {
        launchApp()
        screenshot(1, "app_launched")

        val mainPage = MainPage(device).waitForReady()
        screenshot(2, "main_page_ready")

        mainPage.assertCounterEquals(0)
    }

    @Test
    fun testIncrement() {
        launchApp()

        val mainPage = MainPage(device).waitForReady()
        screenshot(1, "initial_state")

        mainPage.tapIncrement()
        screenshot(2, "after_first_increment")
        mainPage.assertCounterEquals(1)

        mainPage.tapIncrement()
        screenshot(3, "after_second_increment")
        mainPage.assertCounterEquals(2)

        mainPage.increment(3)
        screenshot(4, "after_five_increments")
        mainPage.assertCounterEquals(5)
    }

    @Test
    fun testDecrement() {
        launchApp()

        val mainPage = MainPage(device).waitForReady()

        // Start by incrementing
        mainPage.increment(5)
        screenshot(1, "counter_at_five")
        mainPage.assertCounterEquals(5)

        // Then decrement
        mainPage.tapDecrement()
        screenshot(2, "after_decrement")
        mainPage.assertCounterEquals(4)

        mainPage.decrement(2)
        screenshot(3, "after_more_decrements")
        mainPage.assertCounterEquals(2)
    }

    @Test
    fun testReset() {
        launchApp()

        val mainPage = MainPage(device).waitForReady()

        // Increment several times
        mainPage.increment(10)
        screenshot(1, "counter_at_ten")
        mainPage.assertCounterEquals(10)

        // Reset
        mainPage.tapReset()
        screenshot(2, "after_reset")
        mainPage.assertCounterEquals(0)
    }

    @Test
    fun testNegativeCounter() {
        launchApp()

        val mainPage = MainPage(device).waitForReady()
        screenshot(1, "initial_zero")

        // Decrement below zero
        mainPage.decrement(3)
        screenshot(2, "negative_counter")
        mainPage.assertCounterEquals(-3)

        // Reset should bring back to zero
        mainPage.tapReset()
        screenshot(3, "reset_from_negative")
        mainPage.assertCounterEquals(0)
    }
}
