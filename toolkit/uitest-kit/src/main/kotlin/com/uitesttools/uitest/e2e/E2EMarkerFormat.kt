package com.uitesttools.uitest.e2e

/**
 * Pure, device-independent formatting and validation for E2E markers.
 *
 * This object has NO Android dependencies so it can be exercised by fast local
 * JVM unit tests. The device-side [E2EMarkers] helper and the host-side
 * `android-e2e-runner.sh` bridge both agree on these formats:
 *
 * - **Log channel:** the device logs `APP_E2E_MARKER <name>` via `Log.i`. The
 *   host greps `adb logcat` for that prefix ([LOG_PREFIX]) to sequence the
 *   scenario without pulling any file.
 * - **File channel:** the device writes an empty file `<name>` into the marker
 *   directory ([MARKER_DIR_NAME]) under the app-specific external files dir.
 *   The host `adb pull`s a new marker from one device and `adb push`es it into
 *   the peer's marker directory.
 *
 * Marker names double as file names, so they are validated to be safe, flat
 * file names (no path separators, no whitespace, no traversal).
 */
object E2EMarkerFormat {

    /** Logcat tag used for every marker line. */
    const val TAG: String = "E2EMarker"

    /**
     * Stable prefix the host greps for in `adb logcat`. Kept identical to the
     * iOS `APP_E2E_MARKER` contract so the reactive-orchestration principle and
     * any shared tooling read the same token across platforms.
     */
    const val LOG_PREFIX: String = "APP_E2E_MARKER"

    /**
     * Sub-directory (under the app-specific external files dir) that holds
     * marker files. Never a bare `/sdcard/...` path — see [E2EMarkers] for the
     * scoped-storage rationale.
     */
    const val MARKER_DIR_NAME: String = "e2e-markers"

    /**
     * Characters that are valid in a marker name. Deliberately narrow so the
     * name is always a safe, portable file name on the device's filesystem.
     */
    private val VALID_NAME = Regex("^[A-Za-z0-9._-]+$")

    /** Reserved traversal names that must never be used as a marker. */
    private val RESERVED = setOf(".", "..")

    /**
     * @return `true` if [name] is a legal marker name (non-empty, no path
     *   separators or whitespace, not a traversal token).
     */
    fun isValidName(name: String): Boolean =
        name.isNotEmpty() && name !in RESERVED && VALID_NAME.matches(name)

    /**
     * Validate [name], throwing [IllegalArgumentException] with an actionable
     * message when it is not a legal marker name.
     *
     * @return the same [name] for fluent use.
     */
    fun requireValidName(name: String): String {
        require(isValidName(name)) {
            "Invalid E2E marker name '$name'. Marker names are used as file " +
                "names and must match ${VALID_NAME.pattern} (letters, digits, " +
                "'.', '_', '-'), be non-empty, and not be '.' or '..'."
        }
        return name
    }

    /**
     * Build the logcat line the device emits for a marker.
     *
     * @return e.g. `APP_E2E_MARKER peer_detected`.
     */
    fun logLine(name: String): String = "$LOG_PREFIX ${requireValidName(name)}"

    /**
     * Extract a marker name from a single logcat [line], if it carries one.
     *
     * Tolerant of logcat's leading metadata (timestamp, pid, tag, level) — it
     * anchors on [LOG_PREFIX] and reads the following whitespace-delimited
     * token. Returns `null` when the line has no marker or the token is not a
     * valid marker name.
     *
     * @return the marker name, or `null`.
     */
    fun parseLogLine(line: String): String? {
        val idx = line.indexOf(LOG_PREFIX)
        if (idx < 0) return null
        val rest = line.substring(idx + LOG_PREFIX.length).trim()
        if (rest.isEmpty()) return null
        val token = rest.substringBefore(' ').substringBefore('\t')
        return if (isValidName(token)) token else null
    }
}
