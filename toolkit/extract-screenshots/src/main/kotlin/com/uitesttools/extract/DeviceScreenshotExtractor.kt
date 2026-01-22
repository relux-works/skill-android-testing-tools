package com.uitesttools.extract

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Extracts screenshots from Android device using ADB.
 *
 * Screenshots are stored on device at:
 * /sdcard/Pictures/Screenshots/UITests/
 *
 * With structured naming:
 * Run_{session}__Test_{name}__Step_{NN}__{timestamp}__{description}.png
 */
class DeviceScreenshotExtractor(
    private val devicePath: String = "/sdcard/Pictures/Screenshots/UITests",
    private val serial: String? = null,
    private val verbose: Boolean = false,
    private val echo: (String) -> Unit = ::println
) {

    /**
     * Build ADB command prefix.
     */
    private fun adbCmd(): List<String> {
        return if (serial != null) {
            listOf("adb", "-s", serial)
        } else {
            listOf("adb")
        }
    }

    /**
     * Execute a command and return output.
     */
    private fun exec(vararg args: String, timeout: Long = 60): ProcessResult {
        val cmd = adbCmd() + args.toList()

        if (verbose) {
            echo("  > ${cmd.joinToString(" ")}")
        }

        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor(timeout, TimeUnit.SECONDS)

        if (!exitCode) {
            process.destroyForcibly()
            return ProcessResult(-1, "Command timed out")
        }

        return ProcessResult(process.exitValue(), output.trim())
    }

    /**
     * Check if ADB is available.
     */
    fun checkAdb(): Boolean {
        return try {
            val result = ProcessBuilder("which", "adb")
                .start()
                .waitFor(5, TimeUnit.SECONDS)
            result
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if a device is connected.
     */
    fun checkDevice(): Boolean {
        val result = exec("devices")

        if (result.exitCode != 0) {
            return false
        }

        val lines = result.output.lines().drop(1) // Skip header
        val devices = lines
            .filter { it.isNotBlank() && it.contains("\tdevice") }
            .map { it.split("\t").first() }

        if (devices.isEmpty()) {
            return false
        }

        if (serial != null && serial !in devices) {
            return false
        }

        return true
    }

    /**
     * List screenshots on device.
     */
    fun listScreenshots(): List<String> {
        val result = exec("shell", "ls", "-1", devicePath)

        if (result.exitCode != 0 || result.output.contains("No such file")) {
            return emptyList()
        }

        return result.output
            .lines()
            .filter { it.endsWith(".png") }
    }

    /**
     * Extract all screenshots from device to local directory.
     *
     * @param outputDir Local directory to save screenshots
     * @return List of extracted files
     */
    fun extractScreenshots(outputDir: File): List<File> {
        outputDir.mkdirs()

        val files = listScreenshots()
        if (files.isEmpty()) {
            return emptyList()
        }

        val extracted = mutableListOf<File>()

        // Use adb pull for entire directory (faster than individual files)
        val result = exec("pull", "$devicePath/.", outputDir.absolutePath)

        if (result.exitCode != 0) {
            // Try individual pulls
            for (file in files) {
                val localFile = File(outputDir, file)
                val pullResult = exec("pull", "$devicePath/$file", localFile.absolutePath)

                if (pullResult.exitCode == 0) {
                    extracted.add(localFile)
                    if (verbose) {
                        echo("  Pulled: $file")
                    }
                } else {
                    echo("  Failed to pull: $file")
                }
            }
        } else {
            // List what was pulled
            extracted.addAll(
                outputDir.listFiles()
                    ?.filter { it.extension == "png" }
                    ?: emptyList()
            )
        }

        return extracted
    }

    /**
     * Delete all screenshots from device.
     */
    fun cleanDevice() {
        exec("shell", "rm", "-rf", "$devicePath/*")
    }

    /**
     * Get device info for reporting.
     */
    fun getDeviceInfo(): DeviceInfo? {
        val modelResult = exec("shell", "getprop", "ro.product.model")
        val versionResult = exec("shell", "getprop", "ro.build.version.release")
        val sdkResult = exec("shell", "getprop", "ro.build.version.sdk")

        if (modelResult.exitCode != 0) {
            return null
        }

        return DeviceInfo(
            model = modelResult.output,
            androidVersion = versionResult.output,
            sdkVersion = sdkResult.output.toIntOrNull() ?: 0
        )
    }

    data class ProcessResult(
        val exitCode: Int,
        val output: String
    )

    data class DeviceInfo(
        val model: String,
        val androidVersion: String,
        val sdkVersion: Int
    )
}
