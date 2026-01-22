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
}

rootProject.name = "demo-app"

// === Toggle: local vs remote ===
val useLocalLibs = true  // true = local development, false = git sourceControl

if (useLocalLibs) {
    // Local development: builds from ../toolkit
    includeBuild("../toolkit") {
        dependencySubstitution {
            substitute(module("com.uitesttools:screenshot-kit")).using(project(":screenshot-kit"))
            substitute(module("com.uitesttools:uitest-kit")).using(project(":uitest-kit"))
        }
    }
} else {
    // Remote: Gradle clones repo and builds from git tag
    sourceControl {
        gitRepository(uri("https://github.com/ivalx1s/android-ui-testing-tools.git")) {
            producesModule("com.uitesttools:screenshot-kit")
            producesModule("com.uitesttools:uitest-kit")
        }
    }
}
