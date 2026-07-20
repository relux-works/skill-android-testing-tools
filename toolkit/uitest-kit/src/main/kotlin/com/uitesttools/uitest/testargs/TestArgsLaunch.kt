package com.uitesttools.uitest.testargs

/**
 * Pure, device-independent construction of the **launch side** of the
 * intent-extras IoC convention — the mirror of [TestArgsReader].
 *
 * Two host/test-side jobs live here, both no-Android so they are covered by fast
 * JVM unit tests:
 *
 *  - [amStartExtras] turns typed values into the `am start` extra flags a shell
 *    or `android-device-build.sh` appends (`--ez KEY true`, `--es KEY value`).
 *  - [forward] implements **channel 2**: pick the subset of raw instrumentation
 *    arguments (`InstrumentationRegistry.getArguments()`) whose keys are known
 *    `TestArgs`, so the test can relay them into the launch intent extras that
 *    actually reach the app process.
 *
 * The in-process Intent glue (putting these onto an actual `Intent`) lives in the
 * Android adapter `IntentTestArgs.kt`.
 */
object TestArgsLaunch {

    /**
     * Build the `am start` extra flags for a set of typed test args.
     *
     * Booleans are emitted as `--ez <key> <true|false>` and strings as
     * `--es <key> <value>`. Each token is a **separate list element** (already
     * split the way `ProcessBuilder` / `adb shell am start` want them) — do not
     * join with spaces if you pass them to a process without a shell, or values
     * containing spaces will break.
     *
     * @return a flat token list, e.g.
     *   `["--ez", "MOCK_NETWORK", "true", "--es", "START_SCREEN", "Login"]`.
     */
    fun amStartExtras(
        booleans: Map<String, Boolean> = emptyMap(),
        strings: Map<String, String> = emptyMap(),
    ): List<String> {
        val out = ArrayList<String>(booleans.size * 3 + strings.size * 3)
        for ((key, value) in booleans) {
            out += "--ez"; out += key; out += value.toString()
        }
        for ((key, value) in strings) {
            out += "--es"; out += key; out += value
        }
        return out
    }

    /**
     * Channel 2 — select the instrumentation arguments that are recognized
     * `TestArgs` keys so the test can forward them into the launch intent.
     *
     * `am instrument -e KEY VALUE` reaches only the instrumentation process, so
     * the test reads its own `getArguments()` and relays these onward; unrelated
     * runner args (`class`, `package`, `listener`, …) are dropped.
     *
     * @param instrumentationArgs the raw `getArguments()` map.
     * @param knownKeys the `TestArgs` keys to relay (e.g. the constants declared
     *   in the shared `TestArgs` object).
     * @return the filtered, forward-able subset, preserving supplied values.
     */
    fun forward(
        instrumentationArgs: Map<String, String>,
        knownKeys: Set<String>,
    ): Map<String, String> =
        instrumentationArgs.filterKeys { it in knownKeys }
}
