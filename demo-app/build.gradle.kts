plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    // Paparazzi: JVM-only deterministic snapshot regression (design §6, T5).
    // Runs on the host JVM (no device/emulator). Provides record/verify tasks.
    id("app.cash.paparazzi") version "1.3.5"
}

android {
    namespace = "com.uitesttools.demo"
    // compileSdk is pinned to 34 to stay in lockstep with the stable Paparazzi
    // (1.3.5) layoutlib, which renders against Android 14 (API 34). Paparazzi
    // requires layoutlib API >= compileSdk; the only build that ships an API-36
    // layoutlib is Paparazzi 2.0.0-alpha, which mandates a JDK-21 Gradle runtime
    // that this repo's AGP 8.13.2 + Kotlin 2.0.21 + Gradle 8.13 toolchain cannot
    // run (Kotlin compile-daemon regression). Raise this to 36 together with a
    // Paparazzi 2.x + JDK-21 toolchain bump. See the snapshot-testing skill ref.
    compileSdk = 34

    defaultConfig {
        applicationId = "com.uitesttools.demo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Shared test identifiers — single source of truth for TestTags/TestArgs/TestConsts.
    // Declared as `implementation` so it is visible to BOTH the app main source set
    // (tags applied on UI) and the androidTest source set (tags queried in tests,
    // inherited transitively — no separate androidTestImplementation needed).
    implementation(project(":shared-test-identifiers"))

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Our libraries (resolved via includeBuild or sourceControl in settings.gradle.kts)
    androidTestImplementation("com.uitesttools:screenshot-kit:0.0.1")
    androidTestImplementation("com.uitesttools:uitest-kit:0.0.1")

    // Test dependencies
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("junit:junit:4.13.2")

    // Paparazzi snapshot tests live in the JVM unit-test source set (src/test/).
    // Paparazzi itself is applied via the plugin; only JUnit is needed here.
    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
