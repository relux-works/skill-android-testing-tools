// shared-test-identifiers: single source of truth for test identifiers.
//
// Pure-Kotlin JVM module (no Android deps) so it can be consumed by BOTH:
//   - the :app main source set   (implementation) -> tags applied on UI
//   - the androidTest source set (inherited)      -> tags queried in tests
//
// See references/shared-test-identifiers.md in the android-testing-tools skill
// for the full wiring guide (module vs shared-source-set trade-off, IoC keys).
plugins {
    // Version omitted: the Kotlin plugin is already on the build classpath via the
    // root :app module (kotlin.android, 2.0.21). Both share the kotlin-gradle-plugin.
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}
