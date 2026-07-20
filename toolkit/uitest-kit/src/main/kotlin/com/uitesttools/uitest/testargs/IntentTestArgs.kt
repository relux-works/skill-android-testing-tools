package com.uitesttools.uitest.testargs

import android.content.Intent
import android.os.Bundle

/**
 * Thin Android glue for the intent-extras IoC convention. Everything here is a
 * one-liner over the pure [TestArgsReader]/[TestArgsLaunch] core so the
 * interesting logic stays JVM-testable.
 *
 * ### App side (read the flags that were launched with)
 *
 * ```kotlin
 * // In the app's entry Activity / Application:
 * val args = intent.testArgsReader()
 * if (args.boolean(TestArgs.MOCK_NETWORK)) { /* bind the fake network */ }
 * val start = args.string(TestArgs.START_SCREEN)
 * ```
 *
 * ### Launcher side (put the flags onto the intent)
 *
 * ```kotlin
 * // In a test / PageManager, building the launch intent:
 * intent.putTestArgs(
 *     booleans = mapOf(TestArgs.MOCK_NETWORK to true),
 *     strings  = mapOf(TestArgs.START_SCREEN to "Login"),
 * )
 * ```
 */

/**
 * Adapt an [Intent]'s extras to a [TestArgsReader]. Every extra is normalized to
 * its string form, so a Boolean extra (`--ez KEY true`) and a String extra
 * (`--es KEY value`) both read back uniformly through [TestArgsReader.boolean] /
 * [TestArgsReader.string]. Keys with no extra return `null`.
 */
fun Intent.testArgsReader(): TestArgsReader {
    val extras: Bundle? = this.extras
    return TestArgsReader { key ->
        if (extras != null && extras.containsKey(key)) {
            @Suppress("DEPRECATION")
            extras.get(key)?.toString()
        } else {
            null
        }
    }
}

/** Adapt a [Bundle] of extras to a [TestArgsReader]. See [Intent.testArgsReader]. */
fun Bundle.testArgsReader(): TestArgsReader {
    val extras = this
    return TestArgsReader { key ->
        if (extras.containsKey(key)) {
            @Suppress("DEPRECATION")
            extras.get(key)?.toString()
        } else {
            null
        }
    }
}

/**
 * Put typed test args onto this launch [Intent] as extras. Booleans go through
 * `putExtra(key, Boolean)` (the `--ez` analog) and strings through
 * `putExtra(key, String)` (`--es`), matching what [TestArgsLaunch.amStartExtras]
 * emits for the shell path.
 *
 * @return this intent, for chaining.
 */
fun Intent.putTestArgs(
    booleans: Map<String, Boolean> = emptyMap(),
    strings: Map<String, String> = emptyMap(),
): Intent {
    for ((key, value) in booleans) putExtra(key, value)
    for ((key, value) in strings) putExtra(key, value)
    return this
}
