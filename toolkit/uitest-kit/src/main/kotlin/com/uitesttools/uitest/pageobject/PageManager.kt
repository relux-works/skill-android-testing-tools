package com.uitesttools.uitest.pageobject

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Single entry point that aggregates the Page Objects for one app and owns the
 * cross-cutting concerns a test should not repeat: **orientation-before-launch**,
 * the one [UiDevice], test-directed launch extras, and the `screenshot()`
 * passthrough.
 *
 * This is the Android analog of iOS's `UITest.PageManager`. The important
 * divergence: iOS's PageManager *is* the app handle (`XCUIApplication`). On
 * Android the app runs in a **separate process**, so this default manager drives
 * it cross-process through UIAutomator (`By.res`) + an intent launch — the same
 * path the physical-device and screenshot lanes use, so one manager works on an
 * emulator and a physical device identically. For pure-Compose in-process tests,
 * use [ComposePageManager] instead (a *second* mode, not the default).
 *
 * ### Usage — subclass and expose lazy pages
 *
 * The base is generic (it knows nothing about your screens). A consumer subclass
 * adds the Page Object registry:
 *
 * ```kotlin
 * class AppPages(
 *     device: UiDevice,
 *     orientation: Orientation = Orientation.PORTRAIT,
 *     configureIntent: Intent.() -> Unit = {},
 * ) : PageManager(device, "com.example.app", orientation, configureIntent) {
 *     val loginPage by lazy { LoginPage(device) }
 *     val homePage  by lazy { HomePage(device) }
 * }
 *
 * class LoginFlowTest : BaseUiTestSuite() {
 *     override val packageName = "com.example.app"
 *
 *     @Test fun login() {
 *         val pm = AppPages(device)            // pins orientation, launches app
 *         pm.screenshot(1, "app_launched")     // tests only talk to the manager
 *         val home = pm.loginPage.waitForReady().login("u", "p")
 *         pm.screenshot(2, "logged_in")
 *         assertPageDisplayed(home)
 *     }
 * }
 * ```
 *
 * ### Test-directed IoC (launch extras)
 *
 * Instrumentation `-e KEY VALUE` args do NOT reach the app process on Android, so
 * the app's DI graph is flipped via **intent extras** on the launch intent.
 * Provide them through [configureIntent] with the shared `TestArgs` keys and
 * `com.uitesttools.uitest.testargs.putTestArgs` (the same convention
 * [BaseUiTestSuite.launchApp] uses):
 *
 * ```kotlin
 * AppPages(device, configureIntent = {
 *     putTestArgs(
 *         booleans = mapOf(TestArgs.MOCK_NETWORK to true),
 *         strings  = mapOf(TestArgs.START_SCREEN to "Login"),
 *     )
 * })
 * ```
 *
 * Instantiating the manager launches the app by default (orientation is forced
 * first). Pass `autoLaunch = false` to construct without launching.
 *
 * @param device the shared [UiDevice] every page queries through.
 * @param packageName the app under test's package.
 * @param orientation forced **before** launch so screenshots are never captured
 *   rotated.
 * @param configureIntent applied to the launch intent to add test-directed
 *   extras; defaults to no extras.
 * @param launchTimeoutMs bound on the app-appears wait.
 * @param screenshotCapture the sink for the [screenshot] passthrough.
 * @param context the context used to build the launch intent; defaults to the
 *   instrumentation target (app) context.
 * @param autoLaunch launch the app in the constructor (default `true`).
 */
open class PageManager(
    val device: UiDevice,
    val packageName: String,
    orientation: Orientation = Orientation.PORTRAIT,
    private val configureIntent: Intent.() -> Unit = {},
    private val launchTimeoutMs: Long = DEFAULT_LAUNCH_TIMEOUT_MS,
    private val screenshotCapture: ScreenshotCapture = ScreenshotCapture.Default,
    private val context: Context = ApplicationProvider.getApplicationContext(),
    autoLaunch: Boolean = true,
) {

    init {
        // Force orientation BEFORE launch — a rotated first frame is the #1
        // screenshot-review failure. Freeze rotation regardless of autoLaunch.
        device.setOrientation(orientation)
        if (autoLaunch) launch()
    }

    /**
     * Launch (or relaunch from the launcher) the app under test, applying
     * [extraConfig] (default: the manager's [configureIntent]) to the launch
     * intent, then wait until the app window is on screen.
     *
     * @throws IllegalArgumentException if the package has no launch intent.
     */
    fun launch(extraConfig: Intent.() -> Unit = configureIntent) {
        device.pressHome()
        device.launcherPackageName?.let { launcher ->
            device.wait(Until.hasObject(By.pkg(launcher).depth(0)), launchTimeoutMs)
        }

        context.startActivity(buildLaunchIntent(extraConfig))

        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), launchTimeoutMs)
    }

    /**
     * Build the launch [Intent] for the app, applying [extraConfig] to add
     * extras.
     *
     * Override to customise the launch (e.g. target a specific activity). The
     * default resolves the package's launcher activity and clears the task so
     * each launch starts clean.
     */
    protected open fun buildLaunchIntent(extraConfig: Intent.() -> Unit): Intent {
        val intent = requireNotNull(
            context.packageManager.getLaunchIntentForPackage(packageName)
        ) { "No launch intent for package '$packageName' — is it installed?" }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.extraConfig()
        return intent
    }

    /**
     * Force-stop the app, re-pin [orientation], and launch again applying
     * [extraConfig]. Use between scenarios that need a clean process (e.g. a
     * different IoC configuration).
     */
    fun relaunch(
        orientation: Orientation = Orientation.PORTRAIT,
        extraConfig: Intent.() -> Unit = configureIntent,
    ) {
        terminate()
        device.setOrientation(orientation)
        launch(extraConfig)
    }

    /** Force-stop the app under test. */
    fun terminate() {
        device.executeShellCommand("am force-stop $packageName")
    }

    /** Re-pin the device [orientation] mid-scenario (rare; prefer at launch). */
    fun setOrientation(orientation: Orientation) = device.setOrientation(orientation)

    /**
     * Screenshot passthrough — the manager is the single object a test talks to.
     * Delegates to the configured [ScreenshotCapture].
     */
    fun screenshot(step: Int, description: String) =
        screenshotCapture.capture(step, description)

    /** Screenshot passthrough with an auto-incrementing step number. */
    fun screenshot(description: String) = screenshotCapture.capture(description)

    companion object {
        const val DEFAULT_LAUNCH_TIMEOUT_MS = 5_000L
    }
}
