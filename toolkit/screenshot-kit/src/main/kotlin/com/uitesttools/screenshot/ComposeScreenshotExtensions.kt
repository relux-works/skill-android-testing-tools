package com.uitesttools.screenshot

import android.graphics.Bitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import java.io.File
import java.io.FileOutputStream

/**
 * Compose UI Test extensions for screenshot capture.
 *
 * These extensions work with Compose's SemanticsNodeInteraction
 * to capture screenshots of specific composables.
 *
 * Note: These are optional and only available when using Compose UI Test.
 */

/**
 * Capture a screenshot of a specific Compose node.
 *
 * @param step Step number for screenshot naming
 * @param description Description of what is being captured
 * @return The saved screenshot file, or null if capture failed
 */
fun SemanticsNodeInteraction.captureScreenshot(
    step: Int,
    description: String
): File? {
    return try {
        val bitmap = captureToImage().let { imageBitmap ->
            // Convert ImageBitmap to Android Bitmap
            Bitmap.createBitmap(
                imageBitmap.width,
                imageBitmap.height,
                Bitmap.Config.ARGB_8888
            ).also { bitmap ->
                val pixels = IntArray(imageBitmap.width * imageBitmap.height)
                imageBitmap.readPixels(pixels)
                bitmap.setPixels(
                    pixels,
                    0,
                    imageBitmap.width,
                    0,
                    0,
                    imageBitmap.width,
                    imageBitmap.height
                )
            }
        }

        val session = ScreenshotManager.getSessionId() ?: ScreenshotManager.startSession()
        val testName = ScreenshotManager.getCurrentTestName() ?: "UnknownTest"
        val timestamp = java.text.SimpleDateFormat("HHmmss_SSS", java.util.Locale.US)
            .format(java.util.Date())
        val sanitizedDesc = description
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')

        val filename = ScreenshotManager.buildScreenshotName(
            session = session,
            testName = testName,
            step = step,
            timestamp = timestamp,
            description = sanitizedDesc
        )

        val file = File(ScreenshotManager.outputDirectory, filename)
        file.parentFile?.mkdirs()

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        file
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
