package com.uitesttools.screenshot

import android.graphics.Bitmap
import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Singleton manager for capturing and organizing screenshots during UI tests.
 *
 * Screenshots are saved with structured naming for easy extraction and organization:
 * `Run_{session}__Test_{name}__Step_{NN}__{timestamp}__{description}.png`
 *
 * Usage:
 * ```kotlin
 * class MyUiTest {
 *     companion object {
 *         @BeforeClass @JvmStatic
 *         fun setupClass() {
 *             ScreenshotManager.startSession()
 *         }
 *     }
 *
 *     @Before
 *     fun setup() {
 *         ScreenshotManager.startTest(testName.methodName)
 *     }
 *
 *     @Test
 *     fun testSomething() {
 *         // ... do something
 *         ScreenshotManager.screenshot(1, "initial_state")
 *         // ... do more
 *         ScreenshotManager.screenshot(2, "after_action")
 *     }
 * }
 * ```
 */
object ScreenshotManager {

    private var sessionId: String? = null
    private var sessionDirectory: File? = null
    private var currentTestName: String? = null
    private val stepCounter = AtomicInteger(0)
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val timestampFormat = SimpleDateFormat("HHmmss_SSS", Locale.US)

    /**
     * Base directory where screenshot sessions are saved.
     * Default: /sdcard/Pictures/Screenshots/UITests
     *
     * Each session creates a subfolder: Run_{sessionId}/
     */
    var baseDirectory: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "Screenshots/UITests"
    )
        private set

    /**
     * Current session's output directory.
     * Format: {baseDirectory}/Run_{sessionId}/
     */
    val outputDirectory: File?
        get() = sessionDirectory

    /**
     * Configure custom base directory.
     */
    fun setBaseDirectory(directory: File) {
        baseDirectory = directory
    }

    /**
     * Start a new screenshot session. Call this once in @BeforeClass.
     * Creates a unique session ID and session folder.
     *
     * Screenshots will be saved to: {baseDirectory}/Run_{sessionId}/
     */
    fun startSession(): String {
        sessionId = dateFormat.format(Date())
        sessionDirectory = File(baseDirectory, "Run_$sessionId")
        sessionDirectory!!.mkdirs()
        return sessionId!!
    }

    /**
     * Start capturing screenshots for a specific test.
     * Call this in @Before with the test method name.
     */
    fun startTest(testName: String) {
        currentTestName = sanitizeName(testName)
        stepCounter.set(0)
    }

    /**
     * Get current session ID.
     */
    fun getSessionId(): String? = sessionId

    /**
     * Get current test name.
     */
    fun getCurrentTestName(): String? = currentTestName

    /**
     * Capture a screenshot with structured naming.
     *
     * @param step Step number (1, 2, 3...)
     * @param description Brief description (use snake_case)
     * @return The saved screenshot file, or null if capture failed
     */
    fun screenshot(step: Int, description: String): File? {
        val session = sessionId ?: run {
            startSession()
            sessionId!!
        }

        val testName = currentTestName ?: "UnknownTest"
        val timestamp = timestampFormat.format(Date())
        val sanitizedDesc = sanitizeName(description)

        val filename = buildScreenshotName(
            session = session,
            testName = testName,
            step = step,
            timestamp = timestamp,
            description = sanitizedDesc
        )

        return captureAndSave(filename)
    }

    /**
     * Capture screenshot with auto-incrementing step number.
     */
    fun screenshot(description: String): File? {
        return screenshot(stepCounter.incrementAndGet(), description)
    }

    /**
     * Build the structured screenshot filename.
     * Full name with session prefix (for easy identification when viewing files).
     */
    internal fun buildScreenshotName(
        session: String,
        testName: String,
        step: Int,
        timestamp: String,
        description: String
    ): String {
        val stepPadded = step.toString().padStart(2, '0')
        return "Run_${session}__Test_${testName}__Step_${stepPadded}__${timestamp}__${description}.png"
    }

    /**
     * Sanitize a name for use in filenames.
     * Replaces spaces and special chars with underscores.
     */
    private fun sanitizeName(name: String): String {
        return name
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
    }

    /**
     * Capture screenshot using UiAutomator and save to file.
     */
    private fun captureAndSave(filename: String): File? {
        return try {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val device = UiDevice.getInstance(instrumentation)

            val directory = sessionDirectory ?: run {
                startSession()
                sessionDirectory!!
            }

            val file = File(directory, filename)
            file.parentFile?.mkdirs()

            val success = device.takeScreenshot(file)
            if (success) file else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Reset the manager state. Useful for testing.
     */
    internal fun reset() {
        sessionId = null
        sessionDirectory = null
        currentTestName = null
        stepCounter.set(0)
    }
}
