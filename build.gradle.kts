// Root build.gradle.kts for sourceControl consumers
// Defines group and version for published libraries

plugins {
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

allprojects {
    group = "com.uitesttools"
    version = "0.0.1"
}
