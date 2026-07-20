# Page Manager / Pages aggregator

The aggregator layer that sits on top of the [Page Object pattern](page-object-pattern.md):
one object a test talks to, which owns the cross-cutting concerns the pages
should not each re-implement. It is the Android analog of iOS's
`UITest.PageManager` — with one hard divergence spelled out below.

## Why an aggregator

Without it, every test repeats the same setup and reaches into the base suite:

```kotlin
launchApp()
val login = LoginPage(device).waitForReady()   // build pages inline everywhere
screenshot(1, "login")                          // reach into the suite for shots
```

`PageManager` folds those concerns into one entry point:

- **Orientation before launch.** It forces the requested [`Orientation`] *before*
  the app starts, so extracted screenshots are never captured rotated — the #1
  screenshot-review failure (see the mandatory visual-verify gate in `SKILL.md`).
- **One `UiDevice`.** Every page queries through the same device the manager
  holds; the test never constructs `By` selectors.
- **Lazy pages.** Each Page Object is built on first use, so a test reads
  `pm.loginPage.login(...)`.
- **`screenshot()` passthrough.** `pm.screenshot(1, "login")` delegates to the
  structured-naming `ScreenshotManager`, so tests only talk to the manager — the
  "tests only talk to `pm`" contract iOS has.
- **Test-directed launch extras.** IoC flags go on the launch intent (see below).

## Default mode: UIAutomator (cross-process)

The important divergence from iOS: iOS's PageManager *is* the app handle
(`XCUIApplication`). On Android the app runs in a **separate process**, so the
default `PageManager` drives it cross-process through UIAutomator (`By.res`) plus
an intent launch. That is deliberate — it is the same path the physical-device
and screenshot lanes use, so **one manager works on an emulator and a physical
device identically**.

Subclass `PageManager`, wire the package + orientation, and expose the pages:

```kotlin
import android.content.Intent
import androidx.test.uiautomator.UiDevice
import com.uitesttools.uitest.pageobject.Orientation
import com.uitesttools.uitest.pageobject.PageManager

class AppPages(
    device: UiDevice,
    orientation: Orientation = Orientation.PORTRAIT,
    autoLaunch: Boolean = true,
    configureIntent: Intent.() -> Unit = {},
) : PageManager(device, "com.example.app", orientation, configureIntent, autoLaunch = autoLaunch) {
    val loginPage by lazy { LoginPage(device) }
    val homePage  by lazy { HomePage(device) }
}
```

Instantiating the manager pins orientation and launches the app (pass
`autoLaunch = false` to defer). Then a test only talks to `pm`:

```kotlin
@Test fun login() {
    val pm = AppPages(device)
    pm.screenshot(1, "app_launched")
    val home = pm.loginPage.waitForReady().login("u", "p")
    pm.screenshot(2, "logged_in")
    assertPageDisplayed(home)
}
```

Lifecycle helpers:

- `pm.launch()` — (re)launch from the launcher with the configured extras.
- `pm.relaunch(orientation, extraConfig)` — force-stop, re-pin orientation, launch
  again; use between scenarios that need a clean process.
- `pm.terminate()` — force-stop the app.
- `pm.setOrientation(o)` — re-pin mid-scenario (rare; prefer pinning at launch).

Override `buildLaunchIntent` if you must target a specific activity rather than
the package's default launcher activity.

## Second mode: in-process Compose (`ComposePageManager`)

For tests that are genuinely in-process — a `createComposeRule()` /
`createAndroidComposeRule<MainActivity>()` test of Compose content — reach for
`ComposePageManager`. It drives a `ComposeTestRule` (`onNodeWithTag`), needs no
app launch, and is faster and more precise than `By.res`. It keeps the same
`screenshot()` passthrough.

```kotlin
class MainComposeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private val pm by lazy { AppComposePages(composeRule) }

    @Test fun increments() {
        pm.screenshot(1, "launched")
        pm.mainPage.tapIncrement().assertCounter(1)
        pm.screenshot(2, "incremented")
    }
}

class AppComposePages(rule: ComposeTestRule) : ComposePageManager(rule) {
    val mainPage by lazy { MainComposePage(rule) }
}
```

Compose + UIAutomator note: the in-process `onNodeWithTag` path does **not** need
`Modifier.semantics { testTagsAsResourceId = true }`. That flag is only required
for the cross-process `By.res` path — see
[testtag-engine-matrix.md](testtag-engine-matrix.md).

**Which mode?** Default to `PageManager` (UIAutomator) — it is the one that also
runs on physical devices and matches the screenshot/E2E lanes. Use
`ComposePageManager` only for pure-Compose in-process assertions.

## Test-directed IoC (launch extras)

Instrumentation `-e KEY VALUE` args do **not** reach the app process on Android,
so the iOS `ProcessInfo.arguments` model does not port. `PageManager` delivers
flags as **intent extras** through `configureIntent`, using the shared `TestArgs`
keys and `putTestArgs` (the same convention `BaseUiTestSuite.launchApp` uses):

```kotlin
val pm = AppPages(device) {
    putTestArgs(
        booleans = mapOf(TestArgs.MOCK_NETWORK to true),
        strings  = mapOf(TestArgs.START_SCREEN to "Login"),
    )
}
```

The app reads them off its own launch intent (`intent.testArgsReader()`). Full
detail: [shared-test-identifiers.md](shared-test-identifiers.md) and the
Test-Directed IoC section of `SKILL.md`.

## See also

- @assets/UIStruct/PageManager.kt.template — the aggregator subclass
- @assets/UIStruct/ComposePageManager.kt.template — the in-process variant
- @references/page-object-pattern.md — the pages the manager aggregates
- @references/testtag-engine-matrix.md — the 3-engine tag rule + `testTagsAsResourceId`
