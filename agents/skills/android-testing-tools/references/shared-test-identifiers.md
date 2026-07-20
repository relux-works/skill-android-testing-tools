# Shared Test Identifiers — `shared-test-identifiers` module (wiring guide)

The single source of truth for every test identifier the app and its UI tests
agree on. Three concerns, one artifact, shared by **both** the app main source
set (where tags are applied on the UI) and the `androidTest` source set (where
tags are queried by tests):

| File          | Root object   | Holds                                                        |
|---------------|---------------|-------------------------------------------------------------|
| `TestTags.kt`  | `TestTags`    | BEM UI id strings `{Module}_{Screen}_{Element}_{Type}`      |
| `TestArgs.kt`  | `TestArgs`    | Launch / IoC keys (intent-extra / instrumentation-arg keys) |
| `TestConsts.kt`| `TestConsts`  | Timeouts, test credentials, other shared constants          |

This is the Android analog of iOS `TestEnvShared/UITest/{Identifiers,EnvArgs,Consts}`
compiled into both the app and the UI-test target. On Android the clean way to
"compile into both targets" is a **dedicated Gradle module**.

Reference implementation: `demo-app/shared-test-identifiers/` in this repo — a
pure-Kotlin JVM module consumed by the demo app and its `androidTest` suite.

---

## Recommended: dedicated Gradle module

Why a module and not production `main`: the app should not have to place
test-only constants in its production graph just so the `androidTest` source set
can see them. A separate module keeps the app's production graph clean while both
sides compile against the same artifact. Prefer **pure Kotlin JVM** (no Android
deps) — tag values are just `String`s; `Modifier.testTag(String)`, `By.res(String)`
and `withTagValue` all take plain strings.

### 1. Module layout

```
shared-test-identifiers/
├── build.gradle.kts
└── src/main/kotlin/com/example/testids/
    ├── TestTags.kt      // object TestTags   { object Auth { object Login { … } } }
    ├── TestArgs.kt      // object TestArgs   { const val MOCK_NETWORK = "MOCK_NETWORK"; … }
    └── TestConsts.kt    // object TestConsts { object Timeouts { … }; object TestCredentials { … } }
```

### 2. `shared-test-identifiers/build.gradle.kts`

```kotlin
plugins {
    // Omit the version if the Kotlin plugin is already on the build classpath
    // (e.g. the :app module already applies kotlin.android at the same version).
    // Otherwise pin it: id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}
```

### 3. `settings.gradle.kts` — include the module

```kotlin
rootProject.name = "your-app"

include(":shared-test-identifiers")
include(":app")   // your existing app module(s)
```

### 4. App `build.gradle.kts` — depend on it

```kotlin
dependencies {
    // `implementation` is visible to BOTH the app main source set AND the
    // androidTest source set (androidTest inherits the app's main deps).
    // No separate androidTestImplementation is required.
    implementation(project(":shared-test-identifiers"))
}
```

> If your identifiers module lives in a **different Gradle build** (e.g. a
> published toolkit), wire it with `androidTestImplementation(...)` too, since
> inheritance only applies within the same app build.

### 5. Apply tags on the UI (app `main`)

```kotlin
import com.example.testids.TestTags

Column(modifier = Modifier.testTag(TestTags.Main.SCREEN)) { … }
```

For UIAutomator to see a Compose `testTag`, set on the Compose root:
`Modifier.semantics { testTagsAsResourceId = true }` — see
[testtag-engine-matrix.md](testtag-engine-matrix.md).

### 6. Query tags in tests (`androidTest`)

```kotlin
import com.example.testids.TestTags

override val readyMarker = TestTags.Main.TITLE
val incrementButton get() = device.findObject(By.res(TestTags.Main.INCREMENT_BUTTON))
```

### 7. Verify both sides compile against the module

```bash
./gradlew :shared-test-identifiers:build          # module builds standalone
./gradlew :app:compileDebugKotlin \
          :app:compileDebugAndroidTestKotlin       # both source sets consume it
```

---

## The three files

### `TestTags.kt` — BEM UI ids

Nested Kotlin `object`s, `const val` strings, BEM `{Module}_{Screen}_{Element}_{Type}`
with underscores (resource-id friendly). Index helpers stay idiomatic:

```kotlin
object TestTags {
    object Main {
        const val SCREEN = "Main_Home_Screen_container"
        const val INCREMENT_BUTTON = "Main_Home_Increment_button"
    }
    fun homeItemCard(index: Int) = "Main_Home_Item_${index}_card"
}
```

### `TestArgs.kt` — launch / IoC keys

Keys are shared here (the "no raw arg strings" rule). **Android divergence:**
`am instrument -e KEY VALUE` lands in `InstrumentationRegistry.getArguments()`,
visible to the *test* process, **not** the app-under-test — so a flag cannot flip
the app's DI graph the iOS way. Deliver these keys through one of three channels:

1. **Intent extras** (default, cross-process): PageManager launches the app with
   extras (`am start … --ez MOCK_NETWORK true` / `Intent.putExtra`); the app entry
   point reads extras and swaps bindings.
2. **Instrumentation args forwarded**: test reads `getArguments()`, forwards
   selected keys into the launch intent extras.
3. **Test-only DI** (Hilt `@TestInstallIn`): replace bindings in-process.

```kotlin
object TestArgs {
    const val MOCK_NETWORK = "MOCK_NETWORK"       // Boolean extra (--ez)
    const val START_SCREEN = "START_SCREEN"       // String  extra (--es)
}
```

### `TestConsts.kt` — timeouts & credentials

```kotlin
object TestConsts {
    object Timeouts { const val DEFAULT_MS = 5_000L; const val NETWORK_MS = 30_000L }
    object TestCredentials { const val USERNAME = "test_user"; const val PASSWORD = "test_password_123" }
}
```

---

## Why three top-level objects (not one `object UITest`)

The design sketch shows `UITest.Identifier.…` / `UITest.Args.…`. Kotlin cannot
split one `object UITest` across three files, and typealiases cannot be nested
inside an object — so a literal single-root split is awkward. Three top-level
objects (`TestTags`, `TestArgs`, `TestConsts`) is the idiomatic result, keeps the
already-established `TestTags` root source-compatible, and maps 1:1 to the three
concerns. If you want a single import surface, put all three inside one
`UITest.kt` file under `object UITest { object Identifier {…}; object Args {…}; object Consts {…} }`
and add `typealias TestTags = UITest.Identifier` for source-compat — trade-off is
one large file vs. three focused ones.

**Optional (nice-to-have):** a `@JvmInline value class Tag(val raw: String)` so
tags are type-distinct from arbitrary strings. Default stays plain `String`
constants to keep the template copy-paste-trivial.

---

## Fallback: shared `androidTest`/`main` source set (no extra module)

If the consumer refuses an extra module, put the identifier files in a source set
wired into both `main` and `androidTest`. This works but leaks test-only constants
into the production `main` graph — **prefer the module**. See the older
single-source-set write-up in [shared-identifiers.md](shared-identifiers.md).

---

## See also

- [test-args-ioc.md](test-args-ioc.md) — how `TestArgs` keys reach the app: intent-extras convention, instrumentation-arg forwarding, Hilt test-DI, and why iOS `ProcessInfo` does **not** port
- [testtag-engine-matrix.md](testtag-engine-matrix.md) — one tag, three lookup engines (Compose / UIAutomator / View)
- [accessibility-ids.md](accessibility-ids.md) — BEM naming discipline
- [page-object-pattern.md](page-object-pattern.md) — where tags are consumed in Page Objects
- `assets/TestEnvShared/` — copy-paste templates
