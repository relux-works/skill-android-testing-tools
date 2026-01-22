plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("publish-android-library")
}

android {
    namespace = "com.uitesttools.uitest"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core testing
    implementation(libs.androidx.test.core)
    implementation(libs.androidx.test.core.ktx)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.rules)
    implementation(libs.junit)

    // Espresso
    implementation(libs.espresso.core)
    implementation(libs.espresso.contrib)
    implementation(libs.espresso.intents)

    // UIAutomator
    implementation(libs.uiautomator)

    // Allure (optional)
    compileOnly(libs.allure.kotlin.android)
    compileOnly(libs.allure.kotlin.model)
    compileOnly(libs.allure.kotlin.commons)

    // Screenshot kit
    api(project(":screenshot-kit"))

    // Optional Compose support
    compileOnly(libs.compose.ui.test)
}
