---
name: android-testing-tools
version: 1.1.0
description: |
  Android UI testing toolkit with screenshot validation. Use when:
  (1) Setting up UI test infrastructure with Page Object / PageManager pattern
  (2) Creating test tags/accessibility IDs with structured naming
  (3) Writing UI tests with step-by-step screenshots
  (4) Validating UI via screenshot comparison
  (5) Writing snapshot tests with Paparazzi or Shot
  (6) Comparing snapshot diffs with snapshotsdiff CLI
  (7) Integrating with Allure for test reporting
  (8) Organizing shared test identifiers between app and test targets
  (9) Two-device physical E2E with adb marker synchronization
  (10) Test-directed IoC via instrumentation/intent-extra args
  File types: Kotlin UI tests, Espresso, UIAutomator, Compose UI Test, Allure reports
triggers:
  - android ui test
  - android screenshot
  - espresso test
  - compose test
  - page object android
  - page manager android
  - test tag
  - test tags module
  - ui validation android
  - snapshot test android
  - paparazzi snapshot
  - two device e2e android
  - physical device android test
  - instrumentation args
  - adb marker sync
---

# Android UI Testing Tools Skill

Toolkit for Android UI testing with screenshot validation and Page Object pattern.

## Quick Reference

### Test Tag Naming (BEM-like)

```kotlin
// Pattern: {Module}_{Screen}_{Element}_{Type}
// Examples:
"Auth_Login_Username_input"
"Auth_Login_Submit_button"
"Home_Feed_Post_card"
"Settings_Profile_Avatar_image"

// In Compose:
Modifier.testTag("Auth_Login_Username_input")

// In XML:
android:contentDescription="@string/auth_login_username_input"
```

### Screenshot Workflow

```kotlin
class LoginTest : BaseUiTestSuite() {

    override val packageName = "com.example.app"

    @Test
    fun testSuccessfulLogin() {
        launchApp()
        screenshot(1, "app_launched")

        val loginPage = LoginPage(device).waitForReady()
        screenshot(2, "login_page_ready")

        val homePage = loginPage.login("user@test.com", "password123")
        screenshot(3, "logged_in")

        assertPageDisplayed(homePage)
    }
}
```

### Page Object Pattern

```kotlin
class LoginPage(override val device: UiDevice) : PageElement {

    override val readyMarker = "Auth_Login_Title_text"

    val usernameField: UiObject2?
        get() = device.findObject(By.res("Auth_Login_Username_input"))

    val passwordField: UiObject2?
        get() = device.findObject(By.res("Auth_Login_Password_input"))

    val loginButton: UiObject2?
        get() = device.findObject(By.res("Auth_Login_Submit_button"))

    fun login(username: String, password: String): HomePage {
        usernameField?.text = username
        passwordField?.text = password
        loginButton?.click()
        return HomePage(device).waitForReady()
    }
}
```

### Page Manager / Pages aggregator

Aggregate the pages behind one entry point so a test only ever talks to the
manager — the Android analog of iOS's `UITest.PageManager`. `PageManager`
(in `uitest-kit`) centralizes the cross-cutting concerns: it **forces
orientation before launch** (a rotated first frame is the #1 screenshot-review
failure), holds the one `UiDevice`, launches the app with test-directed intent
extras, and exposes a `screenshot(step, desc)` **passthrough** so tests never
reach into a base suite. Subclass it and add lazy page accessors:

```kotlin
class AppPages(
    device: UiDevice,
    orientation: Orientation = Orientation.PORTRAIT,
    configureIntent: Intent.() -> Unit = {},
) : PageManager(device, "com.example.app", orientation, configureIntent) {
    val loginPage by lazy { LoginPage(device) }
    val homePage  by lazy { HomePage(device) }
}

@Test fun login() {
    val pm = AppPages(device)            // pins portrait, launches the app
    pm.screenshot(1, "app_launched")     // tests only talk to the manager
    val home = pm.loginPage.waitForReady().login("u", "p")
    pm.screenshot(2, "logged_in")
    assertPageDisplayed(home)
}
```

**Default is UIAutomator (cross-process)** so one manager drives an emulator and
a physical device identically. For genuinely in-process Compose tests, use
`ComposePageManager` (drives a `ComposeTestRule` via `onNodeWithTag`, no app
launch) — a second mode, not the default. Pass test-directed IoC flags through
`configureIntent { putTestArgs(...) }` (see the Test-Directed IoC section).
Templates: @assets/UIStruct/PageManager.kt.template,
@assets/UIStruct/ComposePageManager.kt.template. Full guide:
@references/page-manager.md.

## Dependencies

Add to your `build.gradle.kts`:

```kotlin
// In your app's androidTest dependencies
androidTestImplementation("com.uitesttools:screenshot-kit:0.0.1")
androidTestImplementation("com.uitesttools:uitest-kit:0.0.1")

// Standard test dependencies
androidTestImplementation("androidx.test:runner:1.5.2")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
```

## Screenshot Naming Convention

Screenshots are saved in session folders with structured names:

```
/sdcard/Pictures/Screenshots/UITests/
└── Run_{session}/
    ├── Run_{session}__Test_{name}__Step_{NN}__{timestamp}__{description}.png
    └── ...

Example:
/sdcard/Pictures/Screenshots/UITests/
└── Run_20240115_143022/
    ├── Run_20240115_143022__Test_testLogin__Step_01__143025_123__initial_screen.png
    ├── Run_20240115_143022__Test_testLogin__Step_02__143026_456__filled_form.png
    └── Run_20240115_143022__Test_testLogout__Step_01__143030_789__logged_out.png
```

Each test run creates a new `Run_{session}/` folder, keeping runs separated.

After extraction with the CLI, can be further organized into:
```
screenshots/
├── Run_20240115_143022/
│   ├── Test_testLogin/
│   │   ├── Step_01_initial_screen.png
│   │   └── Step_02_filled_form.png
│   └── Test_testLogout/
│       └── Step_01_logged_out.png
```

## CLI Tools

### Extract Screenshots

```bash
# From device to local directory
./Scripts/extract-screenshots.sh ./screenshots

# With options
./Scripts/extract-screenshots.sh ./screenshots \
    --serial emulator-5554 \
    --clean

# Run tests and extract in one command
./Scripts/run-tests-and-extract.sh \
    -module app \
    -testClass com.example.LoginTest \
    -output ./screenshots

# Target a specific device on multi-device machines
./Scripts/run-tests-and-extract.sh --serial emulator-5554

# Manual-install lane (physical / MIUI/Xiaomi devices):
# assembleDebug + assembleAndroidTest -> adb install -r -t -> am instrument -w
./Scripts/run-tests-and-extract.sh --manual-install --serial <device-serial>
```

**Two lanes:**

- **connectedAndroidTest (default)** — Gradle-managed install + run. Fine for
  emulator/CI and most physical devices.
- **`--manual-install`** — assembles the app + androidTest APKs, installs with
  `adb install -r -t`, then runs `adb shell am instrument -w`. Use this on
  physical devices (especially **MIUI/Xiaomi**) where the Gradle-managed install
  phase fails intermittently with `INSTALL_FAILED_USER_RESTRICTED`. Pass
  `--test-runner <package>/<runner>` if the instrumentation component can't be
  auto-discovered.

Extraction is **decoupled** from the run: screenshots are pulled even when the
test run fails, so a red run still yields artifacts to inspect.

### Physical Device Build/Install Lane (`android-device-build.sh`)

For physical devices the contract is **"build means install"**: assemble the
app + androidTest APKs, `adb install -r -t` both, then run instrumentation
directly with `adb shell am instrument -w`. `android-device-build.sh` is the
first-class script for that lane (mirrors iOS `ios-device-build`). Run it from
your Android project root:

```bash
# Build + install + run (auto-selects the single attached device)
./Scripts/android-device-build.sh

# Target a device, restrict to one class or method
./Scripts/android-device-build.sh -serial 535a1632 \
    -testClass com.example.LoginTest#happyPath

# Just (re)install the two APKs, don't run
./Scripts/android-device-build.sh --no-build --no-run

# Re-run instrumentation against already-installed APKs (fast, MIUI-stable)
./Scripts/android-device-build.sh --no-install -testClass com.example.LoginTest

# Pass extra instrumentation args (test-directed IoC / TestArgs)
./Scripts/android-device-build.sh -e useMockAuth=true -e seedUser=demo
```

Key flags: `-module` (default `app`), `-variant` (default `debug`), `-runner`
(`<testPkg>/<runner>`, auto-detected via `pm list instrumentation` otherwise),
`-app-apk`/`-test-apk` to override APK paths. Distinct **exit codes**: `2`
generic install failure, `3` MIUI restriction, `4` instrumentation failed/crashed
(`am instrument` itself returns 0 even on test failures, so the script inspects
the report text).

#### MIUI/HyperOS installer failure (`INSTALL_FAILED_USER_RESTRICTED`)

On MIUI/HyperOS (Xiaomi, Redmi, POCO) `adb install` — and therefore
Gradle-managed `connectedAndroidTest` — fails intermittently with
`INSTALL_FAILED_USER_RESTRICTED`. This is a **device policy block, not an APK
bug.** The script detects this exact string, exits `3`, and prints the fix:

1. Enable **Developer options** (tap *Settings → About phone → MIUI/OS version* 7×).
2. In Developer options enable **USB debugging**, **Install via USB**, and
   **USB debugging (Security settings)** — the last requires a signed-in Mi
   Account and an active SIM/network on the device.
3. Keep the device **unlocked** and confirm the on-screen install prompt.
4. If it still fails, turn **off** *MIUI optimization* in Developer options and reboot.

"Install via USB" re-disables itself over time / after reboot — re-check it if
installs start failing again. Once both APKs are installed, run with
`--no-install` (direct `am instrument`) which does not hit this restriction.

### Snapshot Regression (Paparazzi) — deterministic layer

Paparazzi is the JVM-only, golden-file snapshot regression layer (no
device/emulator). It is **separate** from the instrumented screenshot lane
above. Wired in `demo-app/` — see @references/snapshot-testing.md for the full
setup, multi-config matrix, naming, CI, and the toolchain/`compileSdk`
constraint.

```bash
cd demo-app
./gradlew recordPaparazziDebug   # write/refresh goldens (src/test/snapshots/images/)
./gradlew verifyPaparazziDebug   # fail on any pixel delta (the CI gate)
```

A green `verify` only means "pixels match the goldens" — the **visual-review
gate still applies**: open a new golden (or a failure's golden/actual/delta
under `build/paparazzi/failures/`) with the Read tool before trusting/recording.

### Snapshot Diffs (`snapshotsdiff`) — ad-hoc only on Android

```bash
# Ad-hoc two-image comparison (e.g. render a delta of a Paparazzi failure)
snapshotsdiff golden.png actual.png diff.png
```

> **Android note:** the `snapshotsdiff --artifacts/--tests` **batch mode is
> iOS-shaped** (`__Snapshots__` + xcresult) and does NOT match Paparazzi output.
> On Android use only the single-image mode above; Paparazzi emits its own
> failure deltas. See @references/snapshot-testing.md §snapshotsdiff.

## MANDATORY: Screenshot Verification Gate

**This step is not optional. Never call a UI test run "passing" on the basis of
a green test result alone.** A test can go green while the UI is rotated, black,
squished, or missing elements — the assertions only check what they were told to
check. Extraction produces artifacts precisely so a human/agent can *look*.

After extracting screenshots, **ALWAYS open and visually inspect every one** with
the Read tool (`.temp/.../Run_*/Test_*/Step_*.png`). Verify:

- **Orientation** — UI is upright, not rotated/sideways.
- **Content visibility** — all expected elements are present and readable.
- **Layout** — elements are properly positioned, not squished into a corner.
- **No black/empty screens** — indicates the app didn't launch or didn't render.

Common issues and **Android-flavored** fixes:

| Symptom | Fix |
| --- | --- |
| Rotated UI (landscape frame) | Force orientation in `PageManager`/base suite before launch: `device.setOrientationNatural()` |
| Empty/black screenshot | Add a **readiness wait**, not a sleep, before the shot: `device.wait(Until.hasObject(By.res(readyTag)), timeout)` |
| Partial UI (element off-screen) | `scrollDownUntilFound(tag)` before capturing |

```kotlin
// Base suite / PageManager: pin orientation + wait for ready before capturing
device.setOrientationNatural()                                  // no rotated frames
device.wait(Until.hasObject(By.res(readyMarker)), 5_000)        // no black screens
screenshot(step, "screen_ready")                                // then shoot
```

Only after this visual gate passes is the run trustworthy. If any screenshot
fails the check, fix the cause and re-run — do not hand off a green-but-broken UI.

## Emulator & ADB Commands

### Finding Android SDK Tools

```bash
# SDK typically at:
~/Library/Android/sdk/

# Key binaries:
~/Library/Android/sdk/emulator/emulator
~/Library/Android/sdk/platform-tools/adb
~/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager
```

### Emulator Management

```bash
# List available AVDs (Android Virtual Devices)
emulator -list-avds

# Start emulator (with GUI)
emulator -avd Pixel_9_Pro_XL

# Start emulator headless (no window, for CI)
emulator -avd Pixel_9_Pro_XL -no-audio -no-window &

# Wait for emulator to boot
adb wait-for-device
adb shell getprop sys.boot_completed  # returns 1 when ready
```

### ADB Device Commands

```bash
# List connected devices/emulators
adb devices

# Check device is ready
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release

# Install APK
adb install app-debug.apk
adb install -r app-debug.apk  # reinstall

# Uninstall
adb uninstall com.example.app
```

### Screenshot Extraction

```bash
# Screenshots saved to device at:
/sdcard/Pictures/Screenshots/UITests/

# List screenshots on device
adb shell "ls -la /sdcard/Pictures/Screenshots/UITests/"

# Pull all screenshots
adb pull /sdcard/Pictures/Screenshots/UITests/ ./screenshots/

# Clean screenshots from device
adb shell "rm -rf /sdcard/Pictures/Screenshots/UITests/*"
```

### Running Tests via ADB

```bash
# Run all tests in module
./gradlew :app:connectedDebugAndroidTest

# Run specific test class
./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.example.LoginTest

# Run specific test method
./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.example.LoginTest#testSuccessfulLogin
```

## testTag → Engine Matrix (3 engines, one tag)

iOS has **one** identity API (`.accessibilityIdentifier`). Android resolves the
**same BEM tag** through **three lookup engines**. Apply the tag once, query it
with the engine that matches the layer. Full detail + troubleshooting:
@references/testtag-engine-matrix.md.

| UI layer | Apply the tag | Query it | Engine |
|---|---|---|---|
| **Compose** (in-process) | `Modifier.testTag(TestTags…BUTTON)` | `composeRule.onNodeWithTag(TestTags…BUTTON)` | Compose UI Test |
| **Compose** (cross-process) | same `testTag` **+** root `semantics { testTagsAsResourceId = true }` | `device.findObject(By.res(TestTags…BUTTON))` | UIAutomator |
| **View / XML** | `android:id` / `contentDescription` | `onView(withId(...))` / `onView(withContentDescription(tag))` | Espresso |
| **View / XML** (cross-process) | `android:id` / `contentDescription` | `By.res("id")` / `By.desc(tag)` | UIAutomator |

The Page Object harness defaults to **UIAutomator + `By.res`** so one `PageManager`
drives emulator and physical device identically.

### CRITICAL gotcha: `testTagsAsResourceId`

**UIAutomator cannot see a Compose `testTag` by default!** You MUST add
`testTagsAsResourceId = true` to the root composable, or `By.res(...)` silently
returns `null` — no error, no warning, just a timed-out `waitForAppear()`:

```kotlin
// MainActivity.kt
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTagsAsResourceId

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }  // <-- REQUIRED for By.res()
                ) {
                    // Your app content
                }
            }
        }
    }
}
```

Set it **once on the root** — it propagates to every descendant `testTag`.
`onNodeWithTag` works without it; only the UIAutomator `By.res` path needs it.
This has no iOS analog and is the #1 source of "my selector returns null".

### No raw tag strings (lint)

Route every tag through a `TestTags` / `UITest.Identifier` constant, never a raw
literal — a typo like `By.res("Auth_Lgoin_Submit_button")` compiles fine and
returns `null` at runtime. This is the Android analog of iOS's "no raw launch-arg
strings" rule. Enforce it with the bundled lint:

```bash
# exit 1 if any raw tag literal is passed to a lookup API
agents/skills/android-testing-tools/scripts/check-raw-tags.sh app/src
```

It flags literals in `testTag()`, `onNodeWithTag()`, `By.res()`, `By.desc()`,
`withContentDescription()`, and `waitForResourceId()`, exempting the identifier
definition files. See @references/testtag-engine-matrix.md §3.

## Test-Directed IoC (`TestArgs` via intent extras)

To flip the app's dependencies from a test (mock network, fake clock, seeded
data, forced start screen), share **keys** in `TestArgs` (in the
`shared-test-identifiers` module) and deliver them through **intent extras**.

**Android diverges hard from iOS — do NOT 1:1 port `ProcessInfo`.** On iOS the
app reads `ProcessInfo.processInfo.arguments` in-process. On Android,
`am instrument -e KEY VALUE` (and `getArguments()`) reach the **test process
only — the app-under-test never sees them**. A production `Application`/`Activity`
reading instrumentation args gets nothing and the flag silently no-ops.

Three legit channels (keys shared, preference order):

1. **Intent extras** (default, cross-process) — put extras on the launch intent;
   the app reads them off `intent` and swaps bindings. The real "flag flips the
   graph".
2. **Instrumentation args → forwarded** — the test reads `getArguments()` and
   relays known keys into the launch intent (bridges `am instrument -e …`).
3. **Test-only DI (Hilt `@TestInstallIn`)** — replace bindings in-process;
   strongest but framework-coupled and in-process only. The "if you use Hilt" path.

```kotlin
// Launcher side (test / PageManager) — shared keys, never raw strings:
import com.uitesttools.uitest.testargs.putTestArgs

launchApp {
    putTestArgs(
        booleans = mapOf(TestArgs.MOCK_NETWORK to true),
        strings  = mapOf(TestArgs.SEED_DATASET to "42"),
    )
}

// App side (production code, plain Android APIs, no test-lib dependency):
val seed = intent?.getStringExtra(TestArgs.SEED_DATASET)?.toIntOrNull() ?: 0
```

Shell/physical lane: `adb shell am start -n <pkg>/.MainActivity --ez MOCK_NETWORK true --es SEED_DATASET 42`.

Reusable helpers live in `uitest-kit` (`com.uitesttools.uitest.testargs`):
`TestArgsReader` (typed read over any channel), `TestArgsLaunch.amStartExtras` /
`.forward`, `Intent.testArgsReader()` / `Intent.putTestArgs(...)`, and the
`launchApp { … }` overload. Pure core is JVM-unit-tested; see the full playbook
(incl. the Hilt-test-DI setup) in @references/test-args-ioc.md.

## Physical Two-Device E2E (adb marker sync)

For a scenario that spans **two physical devices** that must observe each other
(device A acts, device B must see the effect — peer discovery, presence, a pushed
update, a call/handshake — then advance), drive it with **observable markers,
never sleeps**. The reactive-orchestration principle ports verbatim from the iOS
two-phone harness; the mechanics are all `adb` + instrumentation args.

Each marker moves over **two channels**: a **log** line (`APP_E2E_MARKER <name>`
via `Log.i`, host greps `adb logcat` for fast sequencing) and a **file** in the
app's marker dir (host `adb pull` → `adb push` into the peer; the peer's
`awaitPeerMarker` sees it — this is the actual cross-device delivery).

**Scoped-storage caveat (the #1 port mistake):** write markers to the
**app-specific external files dir** — `getExternalFilesDir(null)` →
`/sdcard/Android/data/<pkg>/files/e2e-markers/` — **never** bare `/sdcard`, which
needs `MANAGE_EXTERNAL_STORAGE` on API 29+ and silently fails. On some API 30+
OEM images even `adb` traversal into `Android/data` is blocked; fall back to a
`run-as` relay (debuggable build) or the `E2EPeerListener` TCP channel.

```kotlin
import com.uitesttools.uitest.e2e.E2EMarkers

// device B — observer test (long-running), blocks in a marker-wait loop
E2EMarkers.clearMarkers(context)
screenshot(1, "before_peer_action")
E2EMarkers.awaitPeerMarker(context, "action_done", timeoutMs = 60_000)  // guards, never advances
screenshot(2, "peer_action_observed")
E2EMarkers.writeMarker(context, "observed")   // file + APP_E2E_MARKER log
```

Keep the long-running role a **single blocking observer test**; poke transitions
with **separate short `am instrument` invocations** (an instrumentation process
is torn down when its method ends). Host bridge:

```bash
./Scripts/android-e2e-runner.sh \
    --package  com.example.app \
    --runner   com.example.app.test/androidx.test.runner.AndroidJUnitRunner \
    --device-a <serialA> --driver-class   com.example.PokeTest \
    --device-b <serialB> --observer-class com.example.ObserverTest \
    --output   .temp/e2e-run --timeout 300

# No hardware? Validate path/marker/scoped-storage logic offline:
./Scripts/android-e2e-runner.sh --self-test     # internal assertions, exits
./Scripts/android-e2e-runner.sh --dry-run       # prints planned adb commands
```

The runner clears stale markers + logcat, starts the observer (B) then the driver
(A), runs a **reactive copy loop** (new marker on either device → `adb pull` →
`adb push` to the peer), **fails fast** if either instrument exits non-zero, and
collects both devices' artifacts (`logcat-{A,B}.log`, `instrument-{A,B}.log`,
`markers-{A,B}.txt`, `marker-bridge.log`) under `--output`. Marker constants live
in `E2EMarkerFormat` (`uitest-kit` `e2e/`) and are mirrored by the script. Full
principle, adb-vs-iOS mechanics table, and failure matrix:
@references/physical-android-e2e-sync.md.

## Scripts Index

All scripts live in `Scripts/` (run from your Android project root unless noted):

| Script | Purpose | Section |
|---|---|---|
| `check-tools.sh` | Java/Gradle/SDK/ADB/Swift preflight | — |
| `run-tests-and-extract.sh` | Run instrumented tests + extract screenshots (`--serial`, `--manual-install`) | Extract Screenshots |
| `extract-screenshots.sh` | ADB pull + Run/Test/Step organize | Extract Screenshots |
| `android-device-build.sh` | Physical build→install→instrument lane (MIUI failure modes, exit codes) | Physical Device Build/Install Lane |
| `android-e2e-runner.sh` | Two-device marker bridge (`--self-test`/`--dry-run`) | Physical Two-Device E2E |
| `logcat-triage.sh` | Runtime log triage after instrumented/E2E runs | @references/logcat-triage.md |
| `scripts/check-raw-tags.sh` (in skill) | Lint: no raw tag-string literals | testTag → Engine Matrix |
| `setup-project-skills.sh` / `setup-global-skills.sh` | Install this skill into a project / globally | — |

Snapshot regression is Gradle-driven, not a script:
`./gradlew recordPaparazziDebug` / `verifyPaparazziDebug` (see Snapshot
Regression above). `snapshotsdiff` is the macOS ad-hoc single-image diff CLI.

## References

- @references/accessibility-ids.md - Test tag naming conventions
- @references/testtag-engine-matrix.md - 3-engine (Compose/Espresso/UIAutomator) lookup matrix + testTagsAsResourceId gotcha + no-raw-tag lint
- @references/shared-identifiers.md - Sharing IDs between app and tests
- @references/shared-test-identifiers.md - `shared-test-identifiers` module: TestTags/TestArgs/TestConsts wiring
- @references/test-args-ioc.md - Test-directed IoC: TestArgs via intent extras (+ forwarding, + Hilt test-DI); Android's ProcessInfo divergence
- @references/page-object-pattern.md - Page Object implementation guide
- @references/page-manager.md - Page Manager / Pages aggregator: UIAutomator default + in-process Compose mode, orientation-before-launch, screenshot passthrough
- @references/allure-integration.md - Allure TestOps setup
- @references/emulator-adb.md - Emulator and ADB commands
- @references/physical-android-e2e-sync.md - Two-device physical E2E: adb marker sync, scoped-storage caveat, `Scripts/android-e2e-runner.sh`, iOS-vs-Android mechanics
- @references/logcat-triage.md - Runtime log triage for instrumented / E2E runs (`Scripts/logcat-triage.sh`)
- @references/snapshot-testing.md - Paparazzi snapshot regression: Gradle wiring, multi-config, CI, visual-review gate, snapshotsdiff re-scope

## Assets

- @assets/UIStruct/ - Page Object templates
- @assets/TestEnvShared/ - Shared identifier templates

## Compose UI Testing

### Test Tags

```kotlin
// In your Composable
@Composable
fun LoginScreen() {
    Column {
        TextField(
            modifier = Modifier.testTag("Auth_Login_Username_input"),
            // ...
        )
        Button(
            modifier = Modifier.testTag("Auth_Login_Submit_button"),
            onClick = { /* ... */ }
        ) {
            Text("Login")
        }
    }
}
```

### Testing

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun testLoginButton() {
    composeTestRule.setContent {
        LoginScreen()
    }

    composeTestRule
        .onNodeWithTag("Auth_Login_Submit_button")
        .assertIsDisplayed()
        .performClick()
}
```

## Common Patterns

### Wait for Element

```kotlin
// UIAutomator
device.waitForResourceId("Auth_Login_Title_text", timeout = 5000)

// Compose
composeTestRule.waitForTag("Auth_Login_Title_text", timeout = 5000)

// Espresso with extension
onView(withId(R.id.loginButton))
    .waitUntilEnabled(timeout = 5000)
```

### Click at Offset (for Switches)

```kotlin
// UIAutomator - click right side of switch
element.click(Point(element.visibleBounds.right - 10, element.visibleCenter.y))

// Compose
onNodeWithTag("Settings_Notifications_Toggle_switch")
    .clickAtOffset(0.9f, 0.5f)

// Espresso
onView(withId(R.id.switch))
    .clickAtOffset(0.9f, 0.5f)
```

### Scroll and Click

```kotlin
// UIAutomator
device.scrollDownUntilFound(By.res("Settings_Advanced_button"))?.click()

// Compose
onNodeWithTag("Settings_Advanced_button")
    .scrollToAndClick()

// Espresso
onView(withId(R.id.advancedButton))
    .scrollToAndClick()
```

## Project Setup Checklist

1. [ ] Add dependencies to `build.gradle.kts`
2. [ ] Create `testTag` constants in shared module (see @assets/TestEnvShared/)
3. [ ] Set up Page Object classes (see @assets/UIStruct/)
4. [ ] Create base test class extending `BaseUiTestSuite`
5. [ ] Add test tags to all interactive elements
6. [ ] Configure Allure (optional, see @references/allure-integration.md)
7. [ ] Set up CI screenshot extraction
8. [ ] Adopt the MANDATORY screenshot verification gate in the run workflow

## Best Practices

### Test Tag Naming

- Use BEM-like pattern: `{Module}_{Screen}_{Element}_{Type}`
- Be consistent across the app
- Use underscores (Android convention)
- Include element type suffix: `_button`, `_input`, `_text`, `_image`, `_card`

### Page Objects

- One page object per screen
- Use `readyMarker` to wait for page load
- Return new page objects from navigation methods
- Keep selectors private, expose actions

### Screenshots

- Take at meaningful state changes
- Use descriptive names (snake_case)
- Number steps sequentially
- Start session in `@BeforeClass`
- **MANDATORY:** after extraction, visually verify every screenshot with the
  Read tool — see the "Screenshot Verification Gate" section. Green tests are
  not proof the UI rendered correctly.

### Assertions

- Use `assertPageDisplayed()` for navigation verification
- Use `waitFor()` instead of `Thread.sleep()`
- Check specific elements, not just "page loaded"
