package com.uitesttools.uitest.e2e

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local JVM unit tests for the pure marker format/validation contract shared by
 * the device-side helper and the host `android-e2e-runner.sh` bridge.
 */
class E2EMarkerFormatTest {

    @Test
    fun `valid names are accepted`() {
        listOf(
            "peer_detected",
            "peer.stable",
            "mode-restarted",
            "Step1",
            "a",
            "A1._-",
        ).forEach { assertTrue("expected valid: $it", E2EMarkerFormat.isValidName(it)) }
    }

    @Test
    fun `invalid names are rejected`() {
        listOf(
            "",                 // empty
            ".",                // traversal
            "..",               // traversal
            "a/b",              // path separator
            "a b",              // whitespace
            "a\tb",             // tab
            "with:colon",       // reserved-ish char
            "emoji_😀",         // non-ascii
            "../escape",        // traversal attempt
        ).forEach { assertFalse("expected invalid: $it", E2EMarkerFormat.isValidName(it)) }
    }

    @Test
    fun `requireValidName returns name for valid input`() {
        assertEquals("peer_detected", E2EMarkerFormat.requireValidName("peer_detected"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `requireValidName throws for invalid input`() {
        E2EMarkerFormat.requireValidName("../evil")
    }

    @Test
    fun `logLine uses stable prefix`() {
        assertEquals("APP_E2E_MARKER peer_detected", E2EMarkerFormat.logLine("peer_detected"))
        assertEquals(E2EMarkerFormat.LOG_PREFIX, "APP_E2E_MARKER")
    }

    @Test
    fun `parseLogLine round-trips logLine`() {
        val name = "peer_stable"
        assertEquals(name, E2EMarkerFormat.parseLogLine(E2EMarkerFormat.logLine(name)))
    }

    @Test
    fun `parseLogLine tolerates logcat metadata prefix`() {
        val line = "07-21 12:00:00.123  1234  1234 I E2EMarker: APP_E2E_MARKER peer_detected"
        assertEquals("peer_detected", E2EMarkerFormat.parseLogLine(line))
    }

    @Test
    fun `parseLogLine reads only the first token after prefix`() {
        val line = "APP_E2E_MARKER peer_detected extra trailing words"
        assertEquals("peer_detected", E2EMarkerFormat.parseLogLine(line))
    }

    @Test
    fun `parseLogLine returns null when no marker present`() {
        assertNull(E2EMarkerFormat.parseLogLine("just a normal log line"))
        assertNull(E2EMarkerFormat.parseLogLine(""))
    }

    @Test
    fun `parseLogLine returns null when marker token is empty`() {
        assertNull(E2EMarkerFormat.parseLogLine("APP_E2E_MARKER "))
        assertNull(E2EMarkerFormat.parseLogLine("APP_E2E_MARKER"))
    }

    @Test
    fun `parseLogLine returns null for invalid marker token`() {
        assertNull(E2EMarkerFormat.parseLogLine("APP_E2E_MARKER ../evil"))
    }

    @Test
    fun `marker dir name is app-scoped and not raw sdcard`() {
        // Guards the scoped-storage decision: the marker dir is a plain relative
        // sub-dir placed under the app-specific external files dir, never a bare
        // /sdcard path.
        assertEquals("e2e-markers", E2EMarkerFormat.MARKER_DIR_NAME)
        assertFalse(E2EMarkerFormat.MARKER_DIR_NAME.contains("/"))
    }
}
