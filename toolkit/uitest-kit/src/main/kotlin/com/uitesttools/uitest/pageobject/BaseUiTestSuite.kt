package com.uitesttools.uitest.pageobject

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.uitesttools.screenshot.ScreenshotManager
import com.uitesttools.screenshot.ScreenshotSessionRule
import com.uitesttools.screenshot.ScreenshotTestRule
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.TestName

/**
 * Base class for UI test suites using the Page Object pattern.
 *
 * Provides:
 * - UiDevice instance
 * - Screenshot management
 * - App launch utilities
 * - Common assertions
 *
 * Usage:
 * ```kotlin
 * class LoginFlowTest : BaseUiTestSuite() {
 *
 *     override val packageName = "com.example.myapp"
 *
 *     @Test
 *     fun testSuccessfulLogin() {
 *         launchApp()
 *         screenshot(1, "app_launched")
 *
 *         val loginPage = LoginPage(device).waitForReady()
 *         screenshot(2, "login_page_ready")
 *
 *         val homePage = loginPage.login("user", "pass")
 *         screenshot(3, "logged_in")
 *
 *         assertPageDisplayed(homePage)
 *     }
 * }
 * ```
 */
abstract class BaseUiTestSuite {

    companion object {
        /**
         * Session rule for managing screenshot sessions across the test class.
         * Override to customize session behavior.
         */
        @get:ClassRule
        @JvmStatic
        val sessionRule = ScreenshotSessionRule()

        private const val DEFAULT_LAUNCH_TIMEOUT = 5000L
    }

    /**
     * Rule for getting the current test method name.
     */
    @get:Rule
    val testName = TestName()

    /**
     * Rule for managing screenshots per test.
     */
    @get:Rule
    val screenshotRule = ScreenshotTestRule()

    /**
     * The UiDevice instance for UI interactions.
     */
    protected lateinit var device: UiDevice
        private set

    /**
     * The application context.
     */
    protected lateinit var context: Context
        private set

    /**
     * The package name of the app under test.
     * Must be overridden by subclasses.
     */
    abstract val packageName: String

    /**
     * Timeout for app launch (milliseconds).
     */
    open val launchTimeout: Long = DEFAULT_LAUNCH_TIMEOUT

    /**
     * Setup method called before each test.
     */
    @Before
    open fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        context = ApplicationProvider.getApplicationContext()

        // Start screenshot tracking for this test
        ScreenshotManager.startTest(testName.methodName)
    }

    /**
     * Launch the app under test.
     *
     * @param clearState Whether to clear app data before launch
     */
    protected fun launchApp(clearState: Boolean = false) {
        if (clearState) {
            clearAppData()
        }

        // Press home first
        device.pressHome()

        // Wait for launcher
        val launcherPackage = device.launcherPackageName
        device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), launchTimeout)

        // Launch the app
        val intent = context.packageManager
            .getLaunchIntentForPackage(packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        requireNotNull(intent) {
            "Could not get launch intent for package: $packageName"
        }

        context.startActivity(intent)

        // Wait for the app to appear
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), launchTimeout)
    }

    /**
     * Clear the app data (requires shell permissions or debug build).
     */
    protected fun clearAppData() {
        device.executeShellCommand("pm clear $packageName")
    }

    /**
     * Take a screenshot with structured naming.
     */
    protected fun screenshot(step: Int, description: String) {
        screenshotRule.screenshot(step, description)
    }

    /**
     * Take a screenshot with auto-incrementing step number.
     */
    protected fun screenshot(description: String) {
        screenshotRule.screenshot(description)
    }

    /**
     * Assert that a page is currently displayed.
     */
    protected fun assertPageDisplayed(page: PageElement) {
        if (!page.isDisplayed()) {
            throw AssertionError(
                "Expected page '${page::class.simpleName}' to be displayed, " +
                "but ready marker '${page.readyMarker}' was not found"
            )
        }
    }

    /**
     * Assert that a page is NOT currently displayed.
     */
    protected fun assertPageNotDisplayed(page: PageElement) {
        if (page.isDisplayed()) {
            throw AssertionError(
                "Expected page '${page::class.simpleName}' to NOT be displayed, " +
                "but ready marker '${page.readyMarker}' was found"
            )
        }
    }

    /**
     * Wait for a condition with polling.
     *
     * @param timeout Maximum time to wait
     * @param pollInterval Time between checks
     * @param condition The condition to check
     * @return true if condition was met, false if timeout
     */
    protected fun waitFor(
        timeout: Long = 5000,
        pollInterval: Long = 100,
        condition: () -> Boolean
    ): Boolean {
        val endTime = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < endTime) {
            if (condition()) return true
            Thread.sleep(pollInterval)
        }
        return false
    }

    /**
     * Press the back button.
     */
    protected fun pressBack() {
        device.pressBack()
    }

    /**
     * Press the home button.
     */
    protected fun pressHome() {
        device.pressHome()
    }

    /**
     * Open the recent apps.
     */
    protected fun pressRecentApps() {
        device.pressRecentApps()
    }

    /**
     * Rotate the device to landscape.
     */
    protected fun rotateToLandscape() {
        device.setOrientationLandscape()
    }

    /**
     * Rotate the device to portrait.
     */
    protected fun rotateToPortrait() {
        device.setOrientationPortrait()
    }

    /**
     * Reset rotation to natural orientation.
     */
    protected fun resetRotation() {
        device.setOrientationNatural()
    }
}
