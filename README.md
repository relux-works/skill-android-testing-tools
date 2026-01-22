# Android UI Testing Tools

A comprehensive toolkit for Android UI testing with screenshot capture, Page Object pattern support, and AI-assisted test development.

## Features

- **Screenshot Capture**: Structured screenshot naming with automatic organization
- **Page Object Pattern**: Reusable page and component abstractions
- **Espresso Extensions**: Enhanced ViewInteraction with wait helpers
- **UIAutomator Extensions**: Convenient device interaction methods
- **Compose UI Test Support**: Extensions for Jetpack Compose testing
- **Allure Integration**: Rich test reporting with steps and attachments
- **AI Agent Skill**: Claude Code / Codex CLI integration for assisted test development

## Quick Start

### 1. Add Dependencies

```kotlin
// build.gradle.kts (app module)
dependencies {
    androidTestImplementation("com.uitesttools:screenshot-kit:1.0.0")
    androidTestImplementation("com.uitesttools:uitest-kit:1.0.0")

    // Standard test dependencies
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
```

### 2. Create Test Tags

```kotlin
// app/src/main/kotlin/com/example/testenv/TestTags.kt
object TestTags {
    object Auth {
        object Login {
            const val TITLE = "Auth_Login_Title_text"
            const val USERNAME_INPUT = "Auth_Login_Username_input"
            const val SUBMIT_BUTTON = "Auth_Login_Submit_button"
        }
    }
}
```

### 3. Add Test Tags to UI

```kotlin
// Compose - enable testTagsAsResourceId on root
Surface(
    modifier = Modifier
        .fillMaxSize()
        .semantics { testTagsAsResourceId = true }
) {
    // Your content
}

// Then use testTag on elements
TextField(
    modifier = Modifier.testTag(TestTags.Auth.Login.USERNAME_INPUT),
    // ...
)

// XML (contentDescription)
android:contentDescription="Auth_Login_Username_input"
```

### 4. Create Page Objects

```kotlin
class LoginPage(override val device: UiDevice) : PageElement {
    override val readyMarker = TestTags.Auth.Login.TITLE

    val usernameField get() = device.findObject(By.res(TestTags.Auth.Login.USERNAME_INPUT))
    val loginButton get() = device.findObject(By.res(TestTags.Auth.Login.SUBMIT_BUTTON))

    fun login(username: String, password: String): HomePage {
        usernameField?.text = username
        passwordField?.text = password
        loginButton?.click()
        return HomePage(device).waitForReady()
    }
}
```

### 5. Write Tests

```kotlin
class LoginTest : BaseUiTestSuite() {
    override val packageName = "com.example.app"

    @Test
    fun testSuccessfulLogin() {
        launchApp()
        screenshot(1, "app_launched")

        val loginPage = LoginPage(device).waitForReady()
        screenshot(2, "login_page")

        val homePage = loginPage.login("user@test.com", "password123")
        screenshot(3, "logged_in")

        assertPageDisplayed(homePage)
    }
}
```

### 6. Run Tests and Extract Screenshots

```bash
# Run tests
cd toolkit
./gradlew :app:connectedAndroidTest

# Extract screenshots from device
./Scripts/extract-screenshots.sh ./screenshots

# Or use combined script
./Scripts/run-tests-and-extract.sh -module app -output ./screenshots
```

## CLI Tools

### extract-screenshots

Extract screenshots from Android device/emulator:

```bash
# Basic usage
java -jar toolkit/extract-screenshots/build/libs/extract-screenshots.jar ./screenshots

# With options
java -jar toolkit/extract-screenshots/build/libs/extract-screenshots.jar ./screenshots \
    --serial emulator-5554 \
    --clean \
    --device-path /sdcard/Pictures/Screenshots/UITests
```

### snapshotsdiff (macOS only)

Create visual diffs between snapshot images:

```bash
# Build
swift build -c release --package-path toolkit/snapshotsdiff

# Compare two images
./toolkit/snapshotsdiff/.build/release/snapshotsdiff reference.png actual.png diff.png

# Batch compare
./toolkit/snapshotsdiff/.build/release/snapshotsdiff \
    --artifacts ./SnapshotArtifacts \
    --output ./SnapshotDiffs \
    --tests ./AppSnapshotTests
```

## Project Structure

```
android-ui-testing-tools/
├── agents/skills/           # AI agent skill
│   └── android-ui-validation/
├── Scripts/                 # Shell scripts
├── toolkit/                 # Gradle multi-module project
│   ├── screenshot-kit/      # Screenshot capture library
│   ├── uitest-kit/          # Page Objects, extensions, Allure
│   ├── extract-screenshots/ # JVM CLI for screenshot extraction
│   ├── demo-app/            # Demo application
│   ├── snapshotsdiff/       # Swift CLI for visual diffs (macOS)
│   └── gradle/libs.versions.toml
├── .claude/skills -> ../agents/skills
├── .codex/skills -> ../agents/skills
├── CLAUDE.md
└── README.md
```

## AI Agent Skill

Install the skill for AI-assisted test development:

```bash
# Project-local installation
./Scripts/setup-project-skills.sh /path/to/your/project

# Global installation
./Scripts/setup-global-skills.sh
```

The skill provides:
- Test tag naming conventions
- Page Object pattern templates
- Screenshot workflow guidance
- Allure integration setup
- Common testing patterns

## Requirements

- Java 17+
- Android SDK (API 24+)
- Gradle 8.x
- ADB (for screenshot extraction)
- Swift 5.9+ (for snapshotsdiff, macOS only)

## Verification

```bash
# Check prerequisites
./Scripts/check-tools.sh

# Build all modules
cd toolkit
./gradlew build

# Build extract CLI
./gradlew :extract-screenshots:jar

# Build snapshotsdiff (macOS)
swift build -c release --package-path toolkit/snapshotsdiff
```

## Documentation

- [CLAUDE.md](CLAUDE.md) - AI assistant guidance
- [agents/skills/android-ui-validation/SKILL.md](agents/skills/android-ui-validation/SKILL.md) - Full skill documentation
- [agents/skills/android-ui-validation/references/](agents/skills/android-ui-validation/references/) - Detailed guides

## Related Projects

- [swift-ui-testing-tools](https://github.com/example/swift-ui-testing-tools) - iOS equivalent

## License

MIT
