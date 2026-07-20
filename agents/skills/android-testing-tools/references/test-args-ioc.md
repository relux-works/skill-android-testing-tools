# Test-directed IoC — `TestArgs` via intent extras (Android diverges from iOS)

The shared `TestArgs` object (in the [`shared-test-identifiers`](shared-test-identifiers.md)
module) holds the **keys** for test-directed IoC: flags a test flips to swap the
app's real dependencies for fakes (mock network, fake clock, seeded data, forced
start screen). This doc is how those keys actually reach the app on Android —
and why the iOS approach does **not** port.

---

## The divergence (read this first)

**iOS:** the app reads `ProcessInfo.processInfo.arguments`. Launch args are
visible **inside the app process**, so a flag directly flips the DI graph.

**Android:** `am instrument -e KEY VALUE` (and `getArguments()`) land in the
**test / instrumentation process only — the app-under-test process never sees
them.** A production `Application`/`Activity` cannot read them. So:

> ❌ Do **not** 1:1 port the iOS `ProcessInfo` model. An app that tries to read
> instrumentation `-e` args gets nothing and the flag silently no-ops.

Same *taxonomy* (shared typed keys, "no raw arg strings"), different *plumbing*.

---

## Three delivery channels (preference order)

Keys are shared; the channel is how they cross the process boundary.

### 1. Intent extras — default, cross-process

The launcher (a test, or a `PageManager`) puts extras on the app's **launch
intent**; the app entry point reads them off `intent` and swaps bindings. This
is the true analog of "a flag flips the graph" within the process boundary
Android actually has.

**Launcher side** (test / `PageManager`) — use the shared keys, never raw
strings. `uitest-kit` provides `putTestArgs` and a `launchApp { … }` overload:

```kotlin
import com.uitesttools.uitest.testargs.putTestArgs
import com.uitesttools.demo.testids.TestArgs

launchApp {
    putTestArgs(
        booleans = mapOf(TestArgs.MOCK_NETWORK to true, TestArgs.USE_FAKE_CLOCK to true),
        strings  = mapOf(TestArgs.START_SCREEN to "Login"),
    )
}
```

From a shell (physical lane), the same extras as `am start` flags:

```bash
adb shell am start -n com.example/.MainActivity \
    --ez MOCK_NETWORK true --es START_SCREEN Login
```

`TestArgsLaunch.amStartExtras(...)` builds that flag token list for you.

**App side** — production code reads its own intent extras with plain Android
APIs (no test-lib dependency in the app). Use the shared `TestArgs` keys:

```kotlin
// MainActivity.onCreate
val initialCounter = intent?.getStringExtra(TestArgs.SEED_DATASET)?.toIntOrNull() ?: 0
val mockNetwork    = intent?.getBooleanExtra(TestArgs.MOCK_NETWORK, false) ?: false
```

If the app *does* depend on `uitest-kit` (e.g. an internal debug build), the
`intent.testArgsReader()` adapter normalizes every extra to a uniform typed read:

```kotlin
val args = intent.testArgsReader()
if (args.boolean(TestArgs.MOCK_NETWORK)) bindFakeNetwork()
val start = args.string(TestArgs.START_SCREEN)
```

### 2. Instrumentation args → forwarded

Lets `am instrument -e …` from the shell still reach the app, via the test as a
**relay**: the test reads its own `getArguments()`, keeps the known `TestArgs`
keys, and forwards them into the launch intent extras (channel 1).

```kotlin
import androidx.test.platform.app.InstrumentationRegistry
import com.uitesttools.uitest.testargs.TestArgsLaunch

val raw = InstrumentationRegistry.getArguments()   // Bundle -> map
val forwarded = TestArgsLaunch.forward(
    instrumentationArgs = raw.keySet().associateWith { raw.getString(it).orEmpty() },
    knownKeys = setOf(TestArgs.MOCK_NETWORK, TestArgs.START_SCREEN, TestArgs.SEED_DATASET),
)
launchApp {
    forwarded.forEach { (k, v) -> putExtra(k, v) }   // String extras
}
```

`android-device-build.sh -e MOCK_NETWORK=true` puts the arg on `am instrument`;
the test relays it onward. Booleans arrive as strings here — the reader's
`boolean()` parses `"true"/"1"/"yes"/"on"` uniformly.

### 3. Test-only DI (Hilt `@TestInstallIn`) — strongest, framework-coupled

For **in-process instrumented tests** you can replace bindings directly instead
of routing a flag through the intent. This is the cleanest swap when the app
already uses Hilt, but it couples the test setup to the DI framework and only
works for tests that share the app's process (Espresso / Compose UI Test with a
`HiltAndroidRule`), not for out-of-process UIAutomator launches.

**Hilt-test-DI note.** Pattern:

1. Test runner: a custom `AndroidJUnitRunner` that swaps in `HiltTestApplication`.

   ```kotlin
   class HiltTestRunner : AndroidJUnitRunner() {
       override fun newApplication(cl: ClassLoader?, name: String?, ctx: Context?) =
           super.newApplication(cl, HiltTestApplication::class.java.name, ctx)
   }
   ```
   ```kotlin
   // build.gradle.kts
   defaultConfig { testInstrumentationRunner = "com.example.HiltTestRunner" }
   ```

2. Replace a production module with a test module in the `androidTest` source
   set — `@TestInstallIn` uninstalls the real one:

   ```kotlin
   @Module
   @TestInstallIn(components = [SingletonComponent::class], replaces = [NetworkModule::class])
   object FakeNetworkModule {
       @Provides @Singleton fun api(): Api = FakeApi()
   }
   ```

3. In the test, gate on the shared key so shell/CI can still select behavior:

   ```kotlin
   @get:Rule val hilt = HiltAndroidRule(this)
   // Optionally read TestArgs to decide *which* fake to install / seed.
   ```

**When to pick which:** intent extras (1) is the portable default and the only
one that works for out-of-process UIAutomator flows; forwarding (2) bridges the
shell to channel 1; Hilt test-DI (3) is the strongest in-process swap but is the
"if you use Hilt" path — document it, don't mandate it.

---

## Reusable pieces in `uitest-kit`

Package `com.uitesttools.uitest.testargs`:

| Symbol | Kind | Purpose |
|---|---|---|
| `TestArgsReader` | pure Kotlin | typed read (`boolean`/`string`/`int`/`isPresent`) over any channel |
| `TestArgsSource` | pure Kotlin | single `rawValue(key)` lookup each channel adapts to |
| `TestArgsLaunch.amStartExtras` | pure Kotlin | build `--ez/--es` `am start` flag tokens |
| `TestArgsLaunch.forward` | pure Kotlin | channel-2 relay: keep known keys from `getArguments()` |
| `Intent.testArgsReader()` / `Bundle.testArgsReader()` | Android | adapt extras → `TestArgsReader` (extras normalized to string) |
| `Intent.putTestArgs(...)` | Android | put typed extras on a launch intent |
| `BaseUiTestSuite.launchApp { … }` | Android | launch with an intent-configuring lambda |

The pure core has **no Android dependency** and is covered by fast JVM unit
tests (`TestArgsReaderTest`, `TestArgsLaunchTest`) — same split as
`E2EMarkerFormat`. The instrumented `TestArgsIocTest` (demo app) is the
end-to-end reference for the intent-extras path.

---

## See also

- [shared-test-identifiers.md](shared-test-identifiers.md) — where `TestArgs`
  keys live and how the module is wired into both source sets.
- [page-object-pattern.md](page-object-pattern.md) — where a `PageManager`
  centralizes app launch (the natural home for `putTestArgs`).
