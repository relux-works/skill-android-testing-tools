# Snapshot Regression Testing (Paparazzi)

Paparazzi is the **deterministic, JVM-only snapshot regression layer** for this
toolkit (design §6 / improvement §14.6). It plays the exact role that
`swift-snapshot-testing` plays on iOS: golden-file, CI-run, fixed device config,
and **separate** from the manual instrumented screenshot lane (screenshot-kit +
ADB extraction). It runs on the host JVM — **no emulator or device** — so it is
fast and CI-cheap.

Paparazzi is the **default**. Shot is only an alternative for teams that must
snapshot real on-device rendering; it overlaps the instrumented lane and is not
wired here.

## What it is NOT

- Not a replacement for the manual dev-validation screenshots (those verify a
  live app on a device; snapshots verify a pinned, headless render).
- Not a synchronization mechanism for physical/E2E runs.
- Not a substitute for the **mandatory visual-review gate** (below).

## Gradle wiring

The demo app shows the full setup (`demo-app/build.gradle.kts`):

```kotlin
plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("app.cash.paparazzi") version "1.3.5"   // JVM snapshot regression
}

android {
    compileSdk = 34   // MUST match the Paparazzi layoutlib API — see below
    // ...
}

dependencies {
    testImplementation("junit:junit:4.13.2")   // snapshot tests are JVM unit tests
}
```

Snapshot tests live in the **unit-test** source set `src/test/…` (NOT
`androidTest/`, which is the instrumented lane).

## Workflow

```bash
cd demo-app

# Record / refresh golden images (writes src/test/snapshots/images/*.png)
./gradlew recordPaparazziDebug

# Verify against goldens — fails on any pixel delta (the CI gate)
./gradlew verifyPaparazziDebug
```

Goldens are stored in Paparazzi's **default** layout
`src/test/snapshots/images/…` and committed to git. We deliberately do **not**
force the iOS `__Snapshots__` shape.

## Naming — reuse the BEM taxonomy

Snapshot names reuse the same `{Module}_{Screen}_{State}` spine as the test
tags (underscores), e.g. `Main_Home_Default`, `Auth_Login_Error`. This keeps one
taxonomy across tags and snapshots.

## Multi-config (device matrix)

Parameterize a single `@Test` over device configs to mirror the iOS
`[iPhone13Pro, iPhoneSe]` matrix. The demo does this with `DeviceConfig.PIXEL_5`
and a small phone (`DeviceConfig.NEXUS_4`):

```kotlin
@RunWith(Parameterized::class)
class MainScreenSnapshotTest(private val device: NamedDevice) {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = device.config,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun mainHomeDefault() {
        paparazzi.snapshot(name = "Main_Home_Default_${device.label}") {
            MaterialTheme { Surface(color = MaterialTheme.colorScheme.background) { MainScreen() } }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun devices() = listOf(
            NamedDevice("Pixel5", DeviceConfig.PIXEL_5),
            NamedDevice("SmallPhone", DeviceConfig.NEXUS_4),
        )
    }
    data class NamedDevice(val label: String, val config: DeviceConfig) {
        override fun toString() = label
    }
}
```

See `demo-app/src/test/kotlin/com/uitesttools/demo/MainScreenSnapshotTest.kt`
for the working reference (verified green; two goldens committed).

## MANDATORY: visual-review gate

A green `verifyPaparazziDebug` only proves **"pixels match the committed
goldens."** It says nothing about whether those goldens are *correct*.

Rules:

1. **When you first record a golden, OPEN it with the Read tool and verify it**
   (upright, complete, correctly themed) before committing. A wrong golden makes
   every future `verify` green while shipping a broken UI.
2. **When `verifyPaparazziDebug` FAILS**, Paparazzi writes deltas under
   `demo-app/build/paparazzi/failures/`. **Open golden + actual + delta with the
   Read tool.** Only if the *new* rendering is genuinely correct do you
   re-record (`recordPaparazziDebug`) and commit the updated golden. Never
   blindly re-record to turn CI green.

## CI

`.github/workflows/paparazzi.yml` runs `verifyPaparazziDebug` on every PR
touching `demo-app/`/`toolkit/`. It needs **no emulator** — JDK + Android SDK
only. On failure it uploads the delta PNGs as an artifact so the visual-review
gate can be performed from CI.

## snapshotsdiff — demoted to ad-hoc single-image mode (design §6.3)

`snapshotsdiff` (Swift CLI) is shared verbatim with iOS and its **`--artifacts`
/ `--tests` batch mode is iOS-shaped** (`__Snapshots__` + xcresult) and does NOT
match Paparazzi's output. For Android:

- **Use it only for ad-hoc two-image comparison:**
  `snapshotsdiff <golden.png> <actual.png> <diff.png>` — e.g. to render a
  combined visual delta of a Paparazzi failure for a human.
- **Do NOT use the batch `--artifacts/--tests` layout on Android.** Paparazzi
  already emits its own failure deltas, and the visual-review gate is done by
  opening those PNGs directly with the Read tool.

An Android adapter that maps Paparazzi failures into the `snapshotsdiff
--artifacts` layout is intentionally **not** built (extra surface, little gain);
revisit only if unified cross-platform batch diffing is ever requested.

## Version / toolchain constraint (important)

Paparazzi requires its bundled **layoutlib API ≥ the module `compileSdk`**,
because it reflectively reconciles the framework `android.os.Build` (from the
`compileSdk` `android.jar`) against layoutlib's copy. A mismatch throws
`NoSuchElementException` in `Renderer.configureBuildProperties` at render time.

| Paparazzi | layoutlib API | Build JVM | Notes |
| --- | --- | --- | --- |
| **1.3.5** (used here) | 34 (Android 14) | JDK 17 | Stable; matches this repo's toolchain. Requires `compileSdk = 34`. |
| 2.0.0-alpha0x | 35 / 36 | **JDK 21** | Needed for `compileSdk` 35/36, but the Gradle-on-JDK-21 build fails on this repo's AGP 8.13.2 + Kotlin 2.0.21 + Gradle 8.13 (Kotlin compile-daemon regression). |

The demo pins `compileSdk = 34` to stay on stable Paparazzi + JDK 17. To move the
demo to `compileSdk` 36, bump the whole snapshot toolchain together: Paparazzi
2.x + a JDK-21-clean Gradle/Kotlin/AGP set. Keep `compileSdk` and the Paparazzi
version in lockstep.
