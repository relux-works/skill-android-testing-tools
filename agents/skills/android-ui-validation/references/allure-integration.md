# Allure Integration for Android UI Tests

## Overview

Allure provides rich test reporting with:
- Test execution history
- Screenshots and attachments
- Step-by-step execution
- Labels (epic, feature, story)
- Environment information

## Setup

### 1. Add Dependencies

```kotlin
// build.gradle.kts (app module)

plugins {
    id("com.android.application")
    id("io.qameta.allure") version "2.11.2"
}

dependencies {
    androidTestImplementation("io.qameta.allure:allure-kotlin-android:2.4.0")
    androidTestImplementation("io.qameta.allure:allure-kotlin-model:2.4.0")
    androidTestImplementation("io.qameta.allure:allure-kotlin-commons:2.4.0")
}

allure {
    autoconfigure = true
    aspectjWeaver = true
}
```

### 2. Configure Test Runner

```kotlin
// build.gradle.kts

android {
    defaultConfig {
        testInstrumentationRunner = "io.qameta.allure.android.runners.AllureAndroidJUnitRunner"
    }
}
```

Or create custom runner:

```kotlin
// CustomTestRunner.kt
package com.example.test

import android.os.Bundle
import io.qameta.allure.android.runners.AllureAndroidJUnitRunner

class CustomTestRunner : AllureAndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        // Custom configuration
        super.onCreate(arguments)
    }
}
```

## Using Allure Annotations

### Test Class

```kotlin
package com.example.tests

import io.qameta.allure.kotlin.Epic
import io.qameta.allure.kotlin.Feature
import io.qameta.allure.kotlin.Owner
import io.qameta.allure.kotlin.Severity
import io.qameta.allure.kotlin.SeverityLevel
import io.qameta.allure.kotlin.Story
import org.junit.Test

@Epic("Authentication")
@Feature("Login")
@Owner("Mobile Team")
class LoginTest : BaseUiTestSuite() {

    override val packageName = "com.example.app"

    @Test
    @Story("Successful login")
    @Severity(SeverityLevel.CRITICAL)
    fun testSuccessfulLogin() {
        launchApp()

        LoginPage(device)
            .waitForReady()
            .login("user@test.com", "password123")

        assertPageDisplayed(HomePage(device))
    }

    @Test
    @Story("Invalid credentials")
    @Severity(SeverityLevel.NORMAL)
    fun testInvalidCredentials() {
        launchApp()

        LoginPage(device)
            .waitForReady()
            .loginExpectingError("wrong@test.com", "wrongpass")
            .assertErrorMessage("Invalid credentials")
    }
}
```

### Steps

```kotlin
import io.qameta.allure.kotlin.Allure.step

class LoginPage(override val device: UiDevice) : PageElement {

    override val readyMarker = TestTags.Auth.Login.TITLE

    fun login(username: String, password: String): HomePage {
        step("Enter username: $username") {
            usernameField?.text = username
        }

        step("Enter password") {
            passwordField?.text = password
        }

        step("Tap login button") {
            loginButton?.click()
        }

        step("Wait for home page") {
            return HomePage(device).waitForReady()
        }
    }
}
```

### Attachments

```kotlin
import io.qameta.allure.kotlin.Allure

fun screenshotWithAllure(name: String) {
    val screenshot = ScreenshotManager.screenshot(name)
    screenshot?.let { file ->
        Allure.attachment(
            name = name,
            content = file.readBytes(),
            type = "image/png"
        )
    }
}

// In test
@Test
fun testLogin() {
    launchApp()
    screenshotWithAllure("app_launched")

    LoginPage(device).waitForReady()
    screenshotWithAllure("login_page")

    // ...
}
```

### Links

```kotlin
@Test
@Issue("JIRA-123")
@TmsLink("TC-456")
@Link("Documentation", url = "https://docs.example.com/login")
fun testLogin() {
    // ...
}
```

## AllureTrackable Interface

The UITestKit provides an `AllureTrackable` interface for optional Allure support:

```kotlin
import com.uitesttools.uitest.allure.AllureTrackable
import com.uitesttools.uitest.allure.AllureSteps

@Epic("Authentication")
@Feature("Login")
class LoginTest : BaseUiTestSuite(), AllureTrackable {

    override val epic = "Authentication"
    override val feature = "Login"
    override val owner = "Mobile Team"
    override val tags = listOf("smoke", "critical")

    @Test
    fun testLogin() {
        // Use AllureSteps for compatibility
        AllureSteps.step("Launch app") {
            launchApp()
        }

        AllureSteps.step("Navigate to login") {
            LoginPage(device).waitForReady()
        }

        // This works even without Allure dependency
    }
}
```

## Generating Reports

### Run Tests

```bash
./gradlew connectedAndroidTest
```

### Extract Results

```bash
# Results are in:
# app/build/outputs/allure-results/

# Copy to local for report generation
adb pull /sdcard/allure-results ./allure-results
```

### Generate Report

```bash
# Install Allure CLI
brew install allure

# Generate report
allure serve ./app/build/outputs/allure-results

# Or generate static HTML
allure generate ./app/build/outputs/allure-results -o ./allure-report
```

## CI Integration

### GitHub Actions

```yaml
name: UI Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run UI tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 33
          script: ./gradlew connectedAndroidTest

      - name: Upload Allure results
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: allure-results
          path: app/build/outputs/allure-results

      - name: Generate Allure Report
        uses: simple-elf/allure-report-action@master
        if: always()
        with:
          allure_results: app/build/outputs/allure-results

      - name: Deploy to GitHub Pages
        uses: peaceiris/actions-gh-pages@v3
        if: always()
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./allure-report
```

## Best Practices

### 1. Organize with Labels

```kotlin
@Epic("User Management")    // High-level business area
@Feature("Registration")     // Feature being tested
@Story("Email verification") // User story
```

### 2. Severity Levels

| Level | Use For |
|-------|---------|
| BLOCKER | Core functionality, app doesn't work |
| CRITICAL | Major features, significant impact |
| NORMAL | Regular functionality |
| MINOR | Minor issues, workarounds exist |
| TRIVIAL | Cosmetic issues |

### 3. Meaningful Steps

```kotlin
// Good - descriptive steps
step("Enter valid email address") { ... }
step("Verify error message appears") { ... }

// Bad - vague steps
step("Step 1") { ... }
step("Do thing") { ... }
```

### 4. Attach Evidence

- Screenshots at key points
- API responses for debugging
- Device logs on failures

```kotlin
@Test
fun testWithEvidence() {
    try {
        // test code
    } catch (e: Exception) {
        Allure.attachment("device_logs", getDeviceLogs(), "text/plain")
        Allure.attachment("screenshot", takeScreenshot(), "image/png")
        throw e
    }
}
```

## See Also

- [Allure Kotlin Documentation](https://docs.qameta.io/allure-kotlin/)
- [Allure Android Plugin](https://github.com/allure-framework/allure-android)
