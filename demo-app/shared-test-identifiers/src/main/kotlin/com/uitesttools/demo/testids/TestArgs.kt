package com.uitesttools.demo.testids

/**
 * Shared test-directed IoC / launch keys — the "no raw arg strings" rule.
 *
 * Android divergence from iOS: `am instrument -e KEY VALUE` lands in
 * `InstrumentationRegistry.getArguments()` and is visible to the TEST process,
 * NOT the app-under-test process. So a flag cannot flip the app's DI graph the
 * iOS way. Route these keys through one of three channels (preference order):
 *
 *   1. Intent extras   — PageManager launches the app with extras
 *                        (`am start ... --ez MOCK_NETWORK true` / `Intent.putExtra`);
 *                        the app entry point reads extras and swaps bindings.
 *   2. Instrumentation args forwarded — test reads `getArguments()`, then forwards
 *                        selected keys into the launch intent extras.
 *   3. Test-only DI (Hilt `@TestInstallIn`) — replace bindings directly in-process.
 *
 * The KEYS are shared here regardless of which channel delivers them.
 */
object TestArgs {

    /** Swap the network layer for an in-memory fake. Boolean extra (`--ez`). */
    const val MOCK_NETWORK = "MOCK_NETWORK"

    /** Use a fixed/fake clock for deterministic time-based UI. Boolean extra (`--ez`). */
    const val USE_FAKE_CLOCK = "USE_FAKE_CLOCK"

    /** Skip animations / instant transitions for stable screenshots. Boolean extra (`--ez`). */
    const val DISABLE_ANIMATIONS = "DISABLE_ANIMATIONS"

    /** Route the app to a specific start screen. String extra (`--es`). */
    const val START_SCREEN = "START_SCREEN"

    /** Seed the app with a named fixture dataset. String extra (`--es`). */
    const val SEED_DATASET = "SEED_DATASET"
}
