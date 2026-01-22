package com.uitesttools.screenshot

/**
 * Parser and utilities for structured screenshot naming.
 *
 * Directory structure:
 * `{baseDir}/Run_{session}/Run_{session}__Test_{name}__Step_{NN}__{timestamp}__{description}.png`
 *
 * Example:
 * ```
 * /sdcard/Pictures/Screenshots/UITests/
 * └── Run_20240115_143022/
 *     ├── Run_20240115_143022__Test_testLogin__Step_01__143025_123__initial_screen.png
 *     ├── Run_20240115_143022__Test_testLogin__Step_02__143026_456__login_form.png
 *     └── Run_20240115_143022__Test_testLogout__Step_01__143030_789__logout_button.png
 * ```
 */
object ScreenshotNaming {

    /**
     * Parsed components of a screenshot filename.
     */
    data class ParsedScreenshotName(
        val session: String,
        val testName: String,
        val step: Int,
        val timestamp: String,
        val description: String
    ) {
        /**
         * Reconstruct the full filename.
         */
        fun toFilename(): String {
            val stepPadded = step.toString().padStart(2, '0')
            return "Run_${session}__Test_${testName}__Step_${stepPadded}__${timestamp}__${description}.png"
        }

        /**
         * Get the run directory name.
         */
        fun runDirectory(): String = "Run_$session"

        /**
         * Get the test directory name.
         */
        fun testDirectory(): String = "Test_$testName"

        /**
         * Get the step filename (without path).
         */
        fun stepFilename(): String = "Step_${step.toString().padStart(2, '0')}_$description.png"
    }

    // Regex pattern for parsing screenshot names
    private val PATTERN = Regex(
        """Run_([^_]+(?:_[^_]+)?)__Test_([^_]+(?:_[^_]+)*)__Step_(\d+)__(\d+_\d+)__(.+)\.png"""
    )

    // Regex pattern for session folder name: Run_{session}
    private val SESSION_FOLDER_PATTERN = Regex("""Run_(\d{8}_\d{6})""")

    /**
     * Parse a screenshot filename into its components.
     *
     * @param filename The screenshot filename (with or without path)
     * @return Parsed components, or null if filename doesn't match expected format
     */
    fun parse(filename: String): ParsedScreenshotName? {
        // Extract just the filename if a path is provided
        val name = filename.substringAfterLast("/").substringAfterLast("\\")

        val match = PATTERN.matchEntire(name) ?: return null

        return ParsedScreenshotName(
            session = match.groupValues[1],
            testName = match.groupValues[2],
            step = match.groupValues[3].toIntOrNull() ?: return null,
            timestamp = match.groupValues[4],
            description = match.groupValues[5]
        )
    }

    /**
     * Check if a filename matches the expected screenshot naming format.
     */
    fun isValidScreenshotName(filename: String): Boolean {
        return parse(filename) != null
    }

    /**
     * Group screenshots by run session.
     */
    fun groupBySession(filenames: List<String>): Map<String, List<ParsedScreenshotName>> {
        return filenames
            .mapNotNull { parse(it) }
            .groupBy { it.session }
    }

    /**
     * Group screenshots by test name.
     */
    fun groupByTest(filenames: List<String>): Map<String, List<ParsedScreenshotName>> {
        return filenames
            .mapNotNull { parse(it) }
            .groupBy { it.testName }
    }

    /**
     * Sort screenshots by step number.
     */
    fun sortByStep(screenshots: List<ParsedScreenshotName>): List<ParsedScreenshotName> {
        return screenshots.sortedBy { it.step }
    }

    /**
     * Get the target organized path for a screenshot.
     *
     * @param parsed The parsed screenshot name
     * @return Path like "Run_20240115/Test_login/Step_01_initial.png"
     */
    fun getOrganizedPath(parsed: ParsedScreenshotName): String {
        return "${parsed.runDirectory()}/${parsed.testDirectory()}/${parsed.stepFilename()}"
    }

    /**
     * Extract session ID from a folder path.
     *
     * @param path Path containing a Run_XXXXXXXX_XXXXXX folder
     * @return Session ID or null
     */
    fun extractSessionFromPath(path: String): String? {
        val parts = path.replace("\\", "/").split("/")
        for (part in parts.reversed()) {
            SESSION_FOLDER_PATTERN.matchEntire(part)?.let {
                return it.groupValues[1]
            }
        }
        return null
    }
}
