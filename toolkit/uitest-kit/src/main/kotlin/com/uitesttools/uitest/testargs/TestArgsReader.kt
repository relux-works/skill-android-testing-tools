package com.uitesttools.uitest.testargs

/**
 * Pure, channel-agnostic reader for test-directed IoC arguments (the Android
 * analog of iOS `UITest.Args`).
 *
 * ### Why this is not the iOS ProcessInfo approach
 *
 * On iOS the app reads `ProcessInfo.processInfo.arguments`, so a launch flag is
 * visible **inside the app process** and can flip the DI graph directly. On
 * Android that path does not exist: `am instrument -e KEY VALUE` lands in
 * `InstrumentationRegistry.getArguments()` and is visible to the **test /
 * instrumentation process only, NOT the app-under-test process**. Do **not**
 * 1:1 port the ProcessInfo model — it silently no-ops.
 *
 * Instead the keys (defined once in the shared `TestArgs` object) are delivered
 * to the app through one of three channels, in preference order:
 *
 *  1. **Intent extras** (default, cross-process): the launcher puts extras on the
 *     app's launch intent (`am start … --ez MOCK_NETWORK true` / `Intent.putExtra`);
 *     the app entry point reads them and swaps bindings. This is the true analog
 *     of "a flag flips the graph".
 *  2. **Instrumentation args → forwarded**: the test reads `getArguments()` then
 *     relays selected keys into the launch intent extras (see [TestArgsLaunch]).
 *  3. **Test-only DI** (Hilt `@TestInstallIn`): replace bindings in-process for
 *     instrumented tests — strongest, but couples to the DI framework.
 *
 * This reader is deliberately **pure Kotlin with no Android dependency** so it
 * can be exercised by fast local JVM unit tests. Every delivery channel is
 * adapted down to a single string lookup ([TestArgsSource.rawValue]); the
 * Android glue (Intent/Bundle) lives in thin adapters in `IntentTestArgs.kt`.
 *
 * Values arrive normalized to their string form regardless of channel: a Boolean
 * intent extra (`--ez KEY true`) reads back as `"true"`, a String extra
 * (`--es KEY value`) as `"value"`, and a forwarded instrumentation arg as its
 * raw string. [boolean]/[int] parse that uniform string form.
 */
class TestArgsReader(private val source: TestArgsSource) {

    /** @return `true` if [key] was supplied through the underlying channel. */
    fun isPresent(key: String): Boolean = source.rawValue(key) != null

    /**
     * Read a boolean flag.
     *
     * A key that is **absent** returns [default]. A key that is **present**
     * returns `true` only when its value is one of [TRUE_TOKENS]
     * (`true`/`1`/`yes`/`on`, case-insensitive); any other present value —
     * including [FALSE_TOKENS] — returns `false`.
     */
    fun boolean(key: String, default: Boolean = false): Boolean {
        val raw = source.rawValue(key) ?: return default
        return raw.trim().lowercase() in TRUE_TOKENS
    }

    /**
     * Read a string value, or [default] (`null` by default) when the key is
     * absent. An explicitly-supplied empty string is returned as-is.
     */
    fun string(key: String, default: String? = null): String? =
        source.rawValue(key) ?: default

    /**
     * Read an integer value. Returns [default] when the key is absent or when
     * the supplied value is not a valid integer.
     */
    fun int(key: String, default: Int): Int =
        source.rawValue(key)?.trim()?.toIntOrNull() ?: default

    companion object {
        /** Present values (case-insensitive) that read as boolean `true`. */
        val TRUE_TOKENS: Set<String> = setOf("true", "1", "yes", "on")

        /**
         * Present values (case-insensitive) that unambiguously read as `false`.
         * Exposed for callers that want to reject unrecognized tokens; [boolean]
         * itself treats anything outside [TRUE_TOKENS] as `false`.
         */
        val FALSE_TOKENS: Set<String> = setOf("false", "0", "no", "off")

        /**
         * Build a reader over an in-memory map — the shape used both by local
         * JVM unit tests and by instrumentation-arg forwarding (channel 2),
         * where `getArguments()` is already a `Map<String, String>`.
         */
        fun ofMap(values: Map<String, String?>): TestArgsReader =
            TestArgsReader { key -> values[key] }
    }
}

/**
 * The single lookup the [TestArgsReader] needs: the raw string form of the value
 * bound to [key], or `null` when the key was not supplied. Every delivery channel
 * (intent extras, forwarded instrumentation args, an in-memory map) implements
 * this one method.
 */
fun interface TestArgsSource {
    /** @return the raw string form of [key]'s value, or `null` if unset. */
    fun rawValue(key: String): String?
}
