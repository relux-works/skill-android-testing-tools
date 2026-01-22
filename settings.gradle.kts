// Root settings for sourceControl consumers
// Gradle clones the repo and looks for settings.gradle.kts in root

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("toolkit/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "android-ui-testing-tools"

// Include library modules from toolkit/ subdirectory
include(":screenshot-kit")
project(":screenshot-kit").projectDir = file("toolkit/screenshot-kit")

include(":uitest-kit")
project(":uitest-kit").projectDir = file("toolkit/uitest-kit")

// Note: extract-screenshots and demo-app are NOT included here
// They are dev tools, not published libraries
// For local development, use toolkit/settings.gradle.kts directly
