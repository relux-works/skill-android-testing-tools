package com.uitesttools.demo

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Paparazzi snapshot regression for the demo app's main screen.
 *
 * This is the deterministic, JVM-only regression layer described in the design
 * doc §6 (E). It runs on the host JVM with no device/emulator, so it is fast and
 * CI-cheap, and it is intentionally SEPARATE from the manual instrumented
 * screenshot lane (screenshot-kit + ADB extraction).
 *
 * Snapshot naming reuses the BEM taxonomy of the test tags: {Module}_{Screen}_{State}
 * (e.g. Main_Home_Default), same spine as [TestTags].
 *
 * Multi-config: parameterized over two device configs (a modern phone and a small
 * phone), mirroring the iOS reference's [iPhone13Pro, iPhoneSe] matrix.
 *
 * Workflow:
 *   ./gradlew recordPaparazziDebug   # write/refresh golden images
 *   ./gradlew verifyPaparazziDebug   # fail on any pixel delta (CI gate)
 *
 * Goldens live at src/test/snapshots/images/ (Paparazzi default layout — we do
 * NOT force the iOS __Snapshots__ shape). On failure Paparazzi emits its own
 * delta PNGs under build/paparazzi/failures/; open golden/actual/delta with the
 * Read tool as the mandatory visual-review gate before accepting any change.
 */
@RunWith(Parameterized::class)
class MainScreenSnapshotTest(
    private val device: NamedDevice,
) {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = device.config,
        // Material3 theming is supplied by the MaterialTheme composable below;
        // the platform theme only needs to be a no-action-bar light base.
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun mainHomeDefault() {
        paparazzi.snapshot(name = "Main_Home_Default_${device.label}") {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }

    data class NamedDevice(val label: String, val config: DeviceConfig) {
        // Parameterized test display name; also keeps golden filenames readable.
        override fun toString(): String = label
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun devices(): List<NamedDevice> = listOf(
            NamedDevice("Pixel5", DeviceConfig.PIXEL_5),
            NamedDevice("SmallPhone", DeviceConfig.NEXUS_4),
        )
    }
}
