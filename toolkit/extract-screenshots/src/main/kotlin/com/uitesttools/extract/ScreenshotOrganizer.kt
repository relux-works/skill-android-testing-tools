package com.uitesttools.extract

import java.io.File

/**
 * Organizes extracted screenshots into a structured directory hierarchy.
 *
 * Input: Flat directory with files named:
 *   Run_{session}__Test_{name}__Step_{NN}__{timestamp}__{description}.png
 *
 * Output: Organized structure:
 *   Run_{session}/
 *     Test_{name}/
 *       Step_01_{description}.png
 *       Step_02_{description}.png
 */
class ScreenshotOrganizer(
    private val verbose: Boolean = false,
    private val echo: (String) -> Unit = ::println
) {

    // Regex pattern for parsing screenshot names
    private val namePattern = Regex(
        """Run_([^_]+(?:_[^_]+)?)__Test_([^_]+(?:_[^_]+)*)__Step_(\d+)__(\d+_\d+)__(.+)\.png"""
    )

    /**
     * Result of organizing screenshots.
     */
    data class OrganizeResult(
        val runs: Int,
        val tests: Int,
        val screenshots: Int,
        val errors: List<String>
    )

    /**
     * Parsed screenshot name components.
     */
    data class ParsedName(
        val session: String,
        val testName: String,
        val step: Int,
        val timestamp: String,
        val description: String
    ) {
        fun runDir() = "Run_$session"
        fun testDir() = "Test_$testName"
        fun stepFile() = "Step_${step.toString().padStart(2, '0')}_$description.png"
    }

    /**
     * Parse a screenshot filename.
     */
    fun parse(filename: String): ParsedName? {
        val name = filename.substringAfterLast("/").substringAfterLast("\\")
        val match = namePattern.matchEntire(name) ?: return null

        return ParsedName(
            session = match.groupValues[1],
            testName = match.groupValues[2],
            step = match.groupValues[3].toIntOrNull() ?: return null,
            timestamp = match.groupValues[4],
            description = match.groupValues[5]
        )
    }

    /**
     * Organize screenshots from source directory to output directory.
     *
     * @param sourceDir Directory containing raw screenshots
     * @param outputDir Target directory for organized structure
     * @return Organization statistics
     */
    fun organize(sourceDir: File, outputDir: File): OrganizeResult {
        val errors = mutableListOf<String>()
        val runDirs = mutableSetOf<String>()
        val testDirs = mutableSetOf<String>()
        var screenshotCount = 0

        val files = sourceDir.listFiles()
            ?.filter { it.extension == "png" }
            ?: emptyList()

        for (file in files) {
            val parsed = parse(file.name)

            if (parsed == null) {
                errors.add("Could not parse: ${file.name}")
                if (verbose) {
                    echo("  Warning: Could not parse filename: ${file.name}")
                }
                // Copy to "unorganized" folder
                val unorganizedDir = File(outputDir, "unorganized")
                unorganizedDir.mkdirs()
                file.copyTo(File(unorganizedDir, file.name), overwrite = true)
                continue
            }

            val targetDir = File(outputDir, "${parsed.runDir()}/${parsed.testDir()}")
            targetDir.mkdirs()

            val targetFile = File(targetDir, parsed.stepFile())

            try {
                file.copyTo(targetFile, overwrite = true)
                screenshotCount++

                runDirs.add(parsed.runDir())
                testDirs.add("${parsed.runDir()}/${parsed.testDir()}")

                if (verbose) {
                    echo("  ${file.name} -> ${parsed.runDir()}/${parsed.testDir()}/${parsed.stepFile()}")
                }
            } catch (e: Exception) {
                errors.add("Failed to copy ${file.name}: ${e.message}")
            }
        }

        // Create index file
        createIndex(outputDir, runDirs, testDirs, screenshotCount)

        return OrganizeResult(
            runs = runDirs.size,
            tests = testDirs.size,
            screenshots = screenshotCount,
            errors = errors
        )
    }

    /**
     * Create an index.md file summarizing the screenshots.
     */
    private fun createIndex(
        outputDir: File,
        runs: Set<String>,
        tests: Set<String>,
        totalScreenshots: Int
    ) {
        val indexContent = buildString {
            appendLine("# Screenshot Extraction Results")
            appendLine()
            appendLine("- **Total screenshots**: $totalScreenshots")
            appendLine("- **Test runs**: ${runs.size}")
            appendLine("- **Test cases**: ${tests.size}")
            appendLine()
            appendLine("## Runs")
            appendLine()

            for (run in runs.sorted()) {
                appendLine("### $run")
                appendLine()

                val runTests = tests.filter { it.startsWith("$run/") }
                for (test in runTests.sorted()) {
                    val testName = test.substringAfter("$run/")
                    appendLine("- $testName")

                    // List screenshots in this test
                    val testDir = File(outputDir, test)
                    val screenshots = testDir.listFiles()
                        ?.filter { it.extension == "png" }
                        ?.sortedBy { it.name }

                    screenshots?.forEach { screenshot ->
                        appendLine("  - ![${screenshot.nameWithoutExtension}]($test/${screenshot.name})")
                    }
                }

                appendLine()
            }
        }

        File(outputDir, "index.md").writeText(indexContent)
    }

    /**
     * Group screenshots by session/run.
     */
    fun groupByRun(files: List<File>): Map<String, List<File>> {
        return files
            .mapNotNull { file ->
                parse(file.name)?.let { parsed ->
                    parsed.runDir() to file
                }
            }
            .groupBy({ it.first }, { it.second })
    }

    /**
     * Group screenshots by test name.
     */
    fun groupByTest(files: List<File>): Map<String, List<File>> {
        return files
            .mapNotNull { file ->
                parse(file.name)?.let { parsed ->
                    "${parsed.runDir()}/${parsed.testDir()}" to file
                }
            }
            .groupBy({ it.first }, { it.second })
    }
}
