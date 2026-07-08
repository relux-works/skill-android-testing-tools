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

#### Option A: Gradle sourceControl (Recommended)

No registry needed — Gradle clones and builds from git tag directly (like SPM).

```kotlin
// settings.gradle.kts
sourceControl {
    gitRepository(uri("ssh://git@github.com/ivalx1s/android-ui-testing-tools.git")) {
        producesModule("com.uitesttools:screenshot-kit")
        producesModule("com.uitesttools:uitest-kit")
    }
}
```

```kotlin
// build.gradle.kts (app module)
dependencies {
    androidTestImplementation("com.uitesttools:screenshot-kit:0.0.1")
    androidTestImplementation("com.uitesttools:uitest-kit:0.0.1")

    // Standard test dependencies
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
```

#### Option B: GitHub Packages

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/ivalx1s/android-ui-testing-tools")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

```kotlin
// build.gradle.kts (app module)
dependencies {
    androidTestImplementation("com.uitesttools:screenshot-kit:0.0.1")
    androidTestImplementation("com.uitesttools:uitest-kit:0.0.1")
}
```

> **Note**: GitHub Packages requires authentication even for public packages. Add to `~/.gradle/gradle.properties`:
> ```properties
> gpr.user=YOUR_GITHUB_USERNAME
> gpr.key=YOUR_GITHUB_TOKEN
> ```
> Token needs `read:packages` scope. Create at https://github.com/settings/tokens

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

## Real Device Execution

For physical Android devices, especially MIUI/Xiaomi, the most reliable lane is:

1. build the app APK and the androidTest APK
2. install both with `adb install -r -t`
3. run instrumentation directly with `adb shell am instrument`
4. pull screenshots after the run

Why:

- `connectedDebugAndroidTest` / `connectedAndroidTest` often re-enters Gradle-managed install flow
- on MIUI this can fail intermittently with `INSTALL_FAILED_USER_RESTRICTED`
- once APKs are already installed, direct `am instrument` is much more stable

Example:

```bash
./gradlew :app:assembleDebug :app:assembleAndroidTest

adb -s 535a1632 install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s 535a1632 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb -s 535a1632 shell am instrument -w \
  -e class com.example.app.LoginTest \
  com.example.app.test/androidx.test.runner.AndroidJUnitRunner
```

If you also want screenshots copied locally after the run:

```bash
./Scripts/extract-screenshots.sh ./screenshots --serial 535a1632
```

Recommended split:

- emulator / CI lane: `connectedAndroidTest`
- physical-device lane: preinstall + `am instrument`

Practical advice for UI tests on physical devices:

- prefer explicit launch and ready-state waits over assuming the app is foregrounded
- for `UiAutomator`, use text / content-description / resource-id fallbacks instead of relying on a single locator type
- if you use Compose test tags with `UiAutomator`, expose them through `semantics { testTagsAsResourceId = true }` on the root container
- keep screenshot extraction decoupled from test execution so a failed run still leaves artifacts behind

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
├── toolkit/                 # Gradle multi-module project (libraries)
│   ├── screenshot-kit/      # Screenshot capture library
│   ├── uitest-kit/          # Page Objects, extensions, Allure
│   ├── extract-screenshots/ # JVM CLI for screenshot extraction
│   ├── snapshotsdiff/       # Swift CLI for visual diffs (macOS)
│   └── gradle/libs.versions.toml
├── demo-app/                # Demo app (separate Gradle project)
│   └── settings.gradle.kts  # Toggle: useLocalLibs = true/false
├── buildSrc/                # Convention plugins
├── build.gradle.kts         # Root config (for sourceControl)
├── settings.gradle.kts      # Root config (for sourceControl)
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

## Known Issues

### buildSrc duplication

`buildSrc/` exists in both root and `toolkit/` directories. This is intentional — Android Studio fails to sync projects when `toolkit/buildSrc` is a symlink to `../buildSrc`.

```
buildSrc/                      # For sourceControl consumers (root build)
toolkit/buildSrc/              # For includeBuild consumers (copy, not symlink!)
```

**TODO:** Find a way to eliminate duplication without breaking Android Studio sync.

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

<!-- relux-ecosystem:start -->

## About Relux Works

This project is part of the open-source ecosystem of
[Relux Works](https://relux.works), an AI-native software development studio.
We build fixed-price MVPs, rescue vibe-coded apps, run local AI inference, and
train teams to work with coding agents. Much of the infrastructure behind that
work is open source.

- Full catalog: [relux.works/en/open-source](https://relux.works/en/open-source/)
- Agentic enablement: [agent harnesses & team training](https://relux.works/en/agentic-enablement/)
- Hire us the agent-native way: point your assistant at `https://api.relux.works/mcp`
- Contact: ivan@relux.works

<!-- relux-ecosystem:end -->

## License

MIT