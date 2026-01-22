package com.uitesttools.screenshot

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit TestRule that automatically manages screenshot sessions.
 *
 * Usage:
 * ```kotlin
 * class MyUiTest {
 *     @get:Rule
 *     val screenshotRule = ScreenshotTestRule()
 *
 *     @Test
 *     fun testSomething() {
 *         screenshotRule.screenshot(1, "initial_state")
 *         // ... do something
 *         screenshotRule.screenshot(2, "after_action")
 *     }
 * }
 * ```
 *
 * Or use with companion ClassRule for session management:
 * ```kotlin
 * class MyUiTest {
 *     companion object {
 *         @get:ClassRule @JvmStatic
 *         val sessionRule = ScreenshotSessionRule()
 *     }
 *
 *     @get:Rule
 *     val screenshotRule = ScreenshotTestRule()
 * }
 * ```
 */
class ScreenshotTestRule : TestRule {

    private var currentTestName: String? = null

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                // Start test tracking
                currentTestName = description.methodName ?: "UnknownTest"
                ScreenshotManager.startTest(currentTestName!!)

                try {
                    base.evaluate()
                } finally {
                    currentTestName = null
                }
            }
        }
    }

    /**
     * Capture a screenshot for the current test.
     *
     * @param step Step number
     * @param description Brief description
     */
    fun screenshot(step: Int, description: String) {
        ScreenshotManager.screenshot(step, description)
    }

    /**
     * Capture a screenshot with auto-incrementing step number.
     */
    fun screenshot(description: String) {
        ScreenshotManager.screenshot(description)
    }

    /**
     * Get current test name.
     */
    fun getTestName(): String? = currentTestName
}

/**
 * JUnit ClassRule for managing screenshot sessions across a test class.
 *
 * Use as a companion object ClassRule to ensure session is started
 * once per test class.
 */
class ScreenshotSessionRule : TestRule {

    private var sessionId: String? = null

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                // Start session for this test class
                sessionId = ScreenshotManager.startSession()

                try {
                    base.evaluate()
                } finally {
                    sessionId = null
                }
            }
        }
    }

    /**
     * Get the session ID for this test class.
     */
    fun getSessionId(): String? = sessionId
}
