// Root build.gradle.kts for android-ui-testing-tools
// Multi-module project: screenshot-kit, uitest-kit, extract-screenshots

plugins {
    id("com.android.application") version libs.versions.agp apply false
    id("com.android.library") version libs.versions.agp apply false
    id("org.jetbrains.kotlin.android") version libs.versions.kotlin apply false
    id("org.jetbrains.kotlin.jvm") version libs.versions.kotlin apply false
    id("org.jetbrains.kotlin.plugin.compose") version libs.versions.kotlin apply false
}

allprojects {
    group = "com.uitesttools"
    version = "1.0.0"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
