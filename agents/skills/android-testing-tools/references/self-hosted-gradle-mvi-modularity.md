# Self-hosted Gradle dependencies, MVI, and pragmatic modularity

Use this pattern when an Android host application consumes an internal Gradle
library directly from Git without requiring a Maven package registry.

## Contents

- [Keep the library independently buildable](#keep-the-library-independently-buildable)
- [Consume tagged source without a registry](#consume-tagged-source-without-a-registry)
- [Keep tags and versions identical](#keep-tags-and-versions-identical)
- [Substitute a local checkout](#substitute-a-local-checkout)
- [Keep repository access secret-safe](#keep-repository-access-secret-safe)
- [Default host features to one-way MVI](#default-host-features-to-one-way-mvi)
- [Split Gradle modules at real boundaries](#split-gradle-modules-at-real-boundaries)
- [Validate both dependency lanes](#validate-both-dependency-lanes)

## Keep the library independently buildable

Make the library repository a complete Gradle build, not a directory that only
works when copied into an application:

- Commit `settings.gradle.kts`, root build configuration, and the Gradle
  wrapper.
- Keep Android library modules addressable from the repository root.
- Give every consumable module a stable Maven-style `group:artifact`
  coordinate. Treat renaming either side as an API migration.
- Keep app-only plugins, signing, and application resources out of the library
  build.
- Verify the clone directly with `./gradlew clean build`.

For example, keep the group and version explicit in the library build:

```properties
# gradle.properties
GROUP=com.acme.android
VERSION_NAME=1.4.2
```

```kotlin
// Root build.gradle.kts
subprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}
```

With subprojects named `feature-runtime` and `feature-testing`, the stable
coordinates are `com.acme.android:feature-runtime` and
`com.acme.android:feature-testing`.

## Consume tagged source without a registry

Register the Git repository in the host application's `settings.gradle.kts`.
List every module that the source build may provide:

```kotlin
sourceControl {
    gitRepository(uri("ssh://git@github.com/acme/android-foundation.git")) {
        producesModule("com.acme.android:feature-runtime")
        producesModule("com.acme.android:feature-testing")
    }
}
```

Declare ordinary external coordinates in the consuming module:

```kotlin
dependencies {
    implementation("com.acme.android:feature-runtime:1.4.2")
    testImplementation("com.acme.android:feature-testing:1.4.2")
}
```

Gradle matches the requested `group:artifact` to `producesModule`, checks out
the matching Git version, and builds the dependency from source. The internal
library itself does not need Maven Central, GitHub Packages, Artifactory, or
another package registry. The host still needs normal repositories for Android
plugins and the library's third-party dependencies.

Maven publication may be added later for artifact caching, broader
distribution, or consumers that cannot use source dependencies. Treat it as an
optional second distribution lane, not a prerequisite for `sourceControl`.

## Keep tags and versions identical

Use one immutable release identity in all three places:

- Library `VERSION_NAME=1.4.2`
- Git tag `1.4.2`
- Consumer dependency version `1.4.2`

Do not mix `v1.4.2` and `1.4.2`, point a version at a branch, or move a
released tag. Before tagging, build the standalone repository and make CI fail
when the proposed tag differs from `VERSION_NAME`. Create the tag only from the
validated commit:

```bash
test "$(sed -n 's/^VERSION_NAME=//p' gradle.properties)" = "1.4.2"
./gradlew clean build
git tag -a 1.4.2 -m "Release 1.4.2"
```

Pin released consumers to a tag-backed version. Use a local composite for
unreleased development instead of publishing a fake version or moving a tag.

## Substitute a local checkout

Keep the dependency declarations unchanged and conditionally replace the same
coordinates with projects from a local included build:

```kotlin
// Host settings.gradle.kts
val localFoundation = providers.gradleProperty("localFoundation").orNull

if (localFoundation != null) {
    includeBuild(localFoundation) {
        dependencySubstitution {
            substitute(module("com.acme.android:feature-runtime"))
                .using(project(":feature-runtime"))
            substitute(module("com.acme.android:feature-testing"))
                .using(project(":feature-testing"))
        }
    }
}
```

Enable it from a developer-local command or user Gradle properties:

```bash
./gradlew -PlocalFoundation=../android-foundation :app:assembleDebug
```

Do not commit a developer's absolute path. Explicit substitutions are preferred
when publication names may differ from Gradle project names; they also make the
coordinate contract reviewable. The app continues to request
`com.acme.android:feature-runtime:1.4.2` in both lanes.

## Keep repository access secret-safe

- Use an SSH URI without embedded credentials:
  `ssh://git@github.com/acme/android-foundation.git`.
- Grant the smallest practical access, normally a read-only deploy key for CI.
- Load private keys from the workstation keychain/SSH agent or the CI secret
  store at runtime. Keep keys, passphrases, and tokens out of
  `settings.gradle.kts`, repository `gradle.properties`, `local.properties`,
  command history, and logs.
- Pin the Git host key in `known_hosts`; never solve automation by disabling
  host-key verification.
- Run the actual Gradle resolution command in CI. A successful
  `git ls-remote` smoke check is useful but does not prove that the Gradle
  runtime uses the same SSH transport and credentials.
- Never fall back to a credential-bearing HTTPS URL in source control.

## Default host features to one-way MVI

Use explicit `State`, `Intent`, `Reducer`, and `Effect` boundaries unless the
host application already has a coherent unidirectional architecture:

```kotlin
data class FeatureState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = false,
)

sealed interface FeatureIntent {
    data object Refresh : FeatureIntent
    data class Loaded(val items: List<Item>) : FeatureIntent
    data class LoadFailed(val message: String) : FeatureIntent
}

sealed interface FeatureEffect {
    data object LoadItems : FeatureEffect
    data class ShowMessage(val text: String) : FeatureEffect
}

data class Reduction(
    val state: FeatureState,
    val effects: List<FeatureEffect> = emptyList(),
)

fun reduce(state: FeatureState, intent: FeatureIntent): Reduction =
    when (intent) {
        FeatureIntent.Refresh -> Reduction(
            state.copy(isLoading = true),
            effects = listOf(FeatureEffect.LoadItems),
        )
        is FeatureIntent.Loaded ->
            Reduction(state.copy(items = intent.items, isLoading = false))
        is FeatureIntent.LoadFailed -> Reduction(
            state.copy(isLoading = false),
            effects = listOf(FeatureEffect.ShowMessage(intent.message)),
        )
    }
```

Apply one flow: UI emits an `Intent`; the pure `Reducer` returns new `State`
plus zero or more `Effect` commands; an effect handler performs I/O and sends
results back as new intents; UI renders state. Keep durable/renderable data in
state and one-shot work outside it. Do not let composables, activities, or
effect handlers mutate state directly.

The store or `ViewModel` may expose `StateFlow<State>` and a separate effect
stream, but MVI does not require a particular framework. Keep the boundary
testable with reducer unit tests and effect-handler integration tests.

## Split Gradle modules at real boundaries

Start with the fewest modules that preserve dependency direction. Extract a
module when at least one stable boundary exists:

- A reusable public API has multiple consumers or an independent release
  cadence.
- Platform ownership differs, such as pure Kotlin/JVM, Android library, JNI,
  dynamic feature, or an API-level adapter.
- A dependency must be isolated from the rest of the graph.
- Build/test isolation or explicit team ownership pays for the extra Gradle
  configuration and API surface.

Keep classes, reducers, and small features as packages until such a boundary is
real. MVI roles are code boundaries, not automatic Gradle modules. Do not create
one module per screen, reducer, class, or implementation detail. When extracting,
keep the exported API small and make dependency direction obvious.

## Validate both dependency lanes

Run validation from clean checkouts:

```bash
# Standalone library lane
./gradlew clean build

# Tagged sourceControl lane in the host
./gradlew :app:dependencyInsight \
    --dependency feature-runtime \
    --configuration debugRuntimeClasspath
./gradlew :app:assembleDebug test

# Local composite lane in the same host
./gradlew -PlocalFoundation=../android-foundation \
    :app:assembleDebug test
```

Also verify:

- The resolved version equals the immutable Git tag and `VERSION_NAME`.
- Both source and composite lanes provide the same coordinates and variants.
- A fresh CI worker can resolve the private repository without printing
  secrets.
- Optional Maven publication, if present, does not become required by either
  lane.

Official Gradle references:

- [SourceControl API](https://docs.gradle.org/current/javadoc/org/gradle/vcs/SourceControl.html)
- [VersionControlRepository API](https://docs.gradle.org/current/javadoc/org/gradle/vcs/VersionControlRepository.html)
- [Composite builds and dependency substitution](https://docs.gradle.org/current/userguide/composite_builds.html)
