package com.uitesttools.extract

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

/**
 * CLI tool for extracting screenshots from Android device/emulator.
 *
 * Usage:
 *   extract-screenshots [OPTIONS] [OUTPUT_DIR]
 *
 * Options:
 *   --device-path PATH   Path on device (default: /sdcard/Pictures/Screenshots/UITests)
 *   --serial SERIAL      Device serial number (for multiple devices)
 *   --organize           Organize screenshots into Run/Test/Step structure
 *   --clean              Delete screenshots from device after extraction
 *   --help               Show help message
 *
 * Arguments:
 *   OUTPUT_DIR           Output directory (default: ./screenshots)
 */
class ExtractScreenshots : CliktCommand(
    name = "extract-screenshots",
    help = "Extract UI test screenshots from Android device"
) {

    private val outputDir by argument(
        name = "OUTPUT_DIR",
        help = "Output directory for screenshots"
    ).default("./screenshots")

    private val devicePath by option(
        "--device-path", "-p",
        help = "Path on device where screenshots are stored"
    ).default("/sdcard/Pictures/Screenshots/UITests")

    private val serial by option(
        "--serial", "-s",
        help = "Device serial number (for adb -s)"
    )

    private val organize by option(
        "--organize", "-o",
        help = "Organize screenshots into Run/Test/Step structure"
    ).flag(default = true)

    private val clean by option(
        "--clean", "-c",
        help = "Delete screenshots from device after extraction"
    ).flag(default = false)

    private val verbose by option(
        "--verbose", "-v",
        help = "Show verbose output"
    ).flag(default = false)

    override fun run() {
        val output = File(outputDir)

        echo("Extracting screenshots from device...")
        if (verbose) {
            echo("  Device path: $devicePath")
            echo("  Output: ${output.absolutePath}")
            echo("  Serial: ${serial ?: "(auto)"}")
        }

        val extractor = DeviceScreenshotExtractor(
            devicePath = devicePath,
            serial = serial,
            verbose = verbose,
            echo = ::echo
        )

        // Check if adb is available
        if (!extractor.checkAdb()) {
            echo("Error: adb not found in PATH", err = true)
            throw RuntimeException("adb not found")
        }

        // Check device connection
        if (!extractor.checkDevice()) {
            echo("Error: No device connected${serial?.let { " with serial $it" } ?: ""}", err = true)
            throw RuntimeException("No device connected")
        }

        // Extract screenshots
        val rawDir = if (organize) {
            File(output, ".raw")
        } else {
            output
        }

        rawDir.mkdirs()

        val extractedFiles = extractor.extractScreenshots(rawDir)

        if (extractedFiles.isEmpty()) {
            echo("No screenshots found at $devicePath")
            return
        }

        echo("Extracted ${extractedFiles.size} screenshots")

        // Organize if requested
        if (organize) {
            echo("Organizing screenshots...")

            val organizer = ScreenshotOrganizer(verbose = verbose, echo = ::echo)
            val organized = organizer.organize(rawDir, output)

            echo("Organized into ${organized.runs} runs, ${organized.tests} tests")

            // Clean up raw directory
            rawDir.deleteRecursively()
        }

        // Clean from device if requested
        if (clean) {
            echo("Cleaning screenshots from device...")
            extractor.cleanDevice()
        }

        echo("Done! Screenshots saved to: ${output.absolutePath}")
    }
}

fun main(args: Array<String>) = ExtractScreenshots().main(args)
