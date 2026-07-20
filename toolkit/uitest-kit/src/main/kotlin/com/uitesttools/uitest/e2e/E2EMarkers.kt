package com.uitesttools.uitest.e2e

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import java.io.File

/**
 * Device-side helper for two-device physical E2E runs.
 *
 * Markers are the *only* synchronization mechanism between the two devices —
 * never wall-clock sleeps. A test advances its scenario by writing a marker at
 * a safe boundary ([writeMarker]) and by waiting for the peer's marker to
 * arrive ([waitForPeerMarker]); bounded timeouts only *guard* those waits, they
 * never *advance* the scenario.
 *
 * ### Where markers live (scoped-storage caveat)
 *
 * Marker files are written under the **app-specific external files dir**
 * ([Context.getExternalFilesDir]), i.e.
 * `/sdcard/Android/data/<pkg>/files/e2e-markers/`. That directory is:
 *
 * - writable by the app on API 29+ **without** `MANAGE_EXTERNAL_STORAGE` or any
 *   runtime storage permission, and
 * - directly reachable by the host via `adb pull` / `adb push`.
 *
 * Do **not** naïvely port the path to a bare `/sdcard/e2e-markers` — on API 30+
 * that trips scoped storage and the write fails. See
 * `references/physical-android-e2e-sync.md`.
 *
 * ### Two channels
 *
 * Every [writeMarker] both writes the marker file *and* logs
 * `APP_E2E_MARKER <name>` (see [E2EMarkerFormat.logLine]). The host bridge uses
 * the log channel to sequence quickly and the file channel to copy the marker
 * across to the peer device.
 *
 * The [Context] passed in should be the **app under test's** context (from the
 * running app process) so pulls/pushes target the app's external files dir.
 * When the marker helper runs inside the instrumentation process, pass the
 * instrumentation target context. [instrumentationTargetContext] resolves it.
 */
object E2EMarkers {

    /**
     * Resolve the marker directory for [context], creating it if necessary.
     *
     * @return the `e2e-markers` directory under the app-specific external files
     *   dir.
     * @throws IllegalStateException if external storage is unavailable so the
     *   external files dir cannot be resolved.
     */
    fun markerDirectory(context: Context): File {
        val base = context.getExternalFilesDir(null)
            ?: throw IllegalStateException(
                "getExternalFilesDir(null) returned null — external storage is " +
                    "unavailable; cannot host E2E markers."
            )
        val dir = File(base, E2EMarkerFormat.MARKER_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * The on-device absolute path of the marker directory. Useful for building
     * `adb pull`/`adb push` commands from the host or logging the location.
     */
    fun markerDirectoryPath(context: Context): String =
        markerDirectory(context).absolutePath

    /**
     * Write a marker at a safe advance point.
     *
     * Writes an empty file named [name] into the marker directory and logs
     * `APP_E2E_MARKER <name>`. Idempotent: re-writing an existing marker just
     * refreshes it.
     *
     * @param name a valid marker name (see [E2EMarkerFormat.requireValidName]).
     * @return the marker [File] that was written.
     */
    fun writeMarker(context: Context, name: String): File {
        E2EMarkerFormat.requireValidName(name)
        val file = File(markerDirectory(context), name)
        // Empty file — presence is the signal. Recreate to refresh mtime.
        file.writeText("")
        Log.i(E2EMarkerFormat.TAG, E2EMarkerFormat.logLine(name))
        return file
    }

    /**
     * @return `true` if a peer marker named [name] is present locally (i.e. the
     *   host bridge has already pushed it into this device's marker dir).
     */
    fun hasPeerMarker(context: Context, name: String): Boolean {
        E2EMarkerFormat.requireValidName(name)
        return File(markerDirectory(context), name).exists()
    }

    /**
     * Block until the peer marker [name] appears locally, or [timeoutMs]
     * elapses.
     *
     * This is a *guarded* wait: the marker file appearing is the observable
     * fact that advances the scenario; [timeoutMs] only bounds the wait so a
     * stuck peer fails the test instead of hanging forever. Polling uses
     * [pollIntervalMs]; no fixed sleep is ever used to *assume* the peer
     * advanced.
     *
     * @param timeoutMs maximum time to wait (default 60s).
     * @param pollIntervalMs interval between existence checks (default 500ms).
     * @return `true` if the marker appeared within the timeout, `false`
     *   otherwise.
     */
    fun waitForPeerMarker(
        context: Context,
        name: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        pollIntervalMs: Long = DEFAULT_POLL_MS,
    ): Boolean {
        E2EMarkerFormat.requireValidName(name)
        val target = File(markerDirectory(context), name)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (target.exists()) return true
            Thread.sleep(pollIntervalMs)
        }
        return target.exists()
    }

    /**
     * Like [waitForPeerMarker] but throws instead of returning `false`, so a
     * missing peer marker fails the test loudly with an actionable message.
     */
    fun awaitPeerMarker(
        context: Context,
        name: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        pollIntervalMs: Long = DEFAULT_POLL_MS,
    ) {
        if (!waitForPeerMarker(context, name, timeoutMs, pollIntervalMs)) {
            throw AssertionError(
                "Timed out after ${timeoutMs}ms waiting for peer E2E marker " +
                    "'$name' in ${markerDirectoryPath(context)}. The host bridge " +
                    "did not push it — check the peer device advanced and the " +
                    "runner is still copying markers."
            )
        }
    }

    /**
     * Delete every marker file in the marker directory. Call at the start of a
     * run so stale markers from a previous run cannot advance the scenario.
     */
    fun clearMarkers(context: Context) {
        val dir = markerDirectory(context)
        dir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Resolve the instrumentation **target** context (the app under test), for
     * callers running inside the instrumentation process that want markers to
     * land in the app's external files dir.
     */
    fun instrumentationTargetContext(): Context =
        ApplicationProvider.getApplicationContext()

    private const val DEFAULT_TIMEOUT_MS = 60_000L
    private const val DEFAULT_POLL_MS = 500L
}
