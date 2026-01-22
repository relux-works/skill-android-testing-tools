---
name: android-ui-validation
version: 1.0.0
description: |
  Android UI testing toolkit with screenshot validation. Use when:
  (1) Setting up UI test infrastructure with Page Object pattern
  (2) Creating test tags/accessibility IDs with structured naming
  (3) Writing UI tests with step-by-step screenshots
  (4) Validating UI via screenshot comparison
  (5) Writing snapshot tests with Paparazzi or Shot
  (6) Comparing snapshot diffs with snapshotsdiff CLI
  (7) Integrating with Allure for test reporting
  (8) Organizing shared test identifiers between app and test targets
  File types: Kotlin UI tests, Espresso, UIAutomator, Compose UI Test, Allure reports
triggers:
  - android ui test
  - android screenshot
  - espresso test
  - compose test
  - page object android
  - test tag
  - ui validation android
  - snapshot test android
---

# Android UI Validation Skill

Toolkit for Android UI testing with screenshot validation and Page Object pattern.

## Quick Reference

### Test Tag Naming (BEM-like)

```kotlin
// Pattern: {Module}_{Screen}_{Element}_{Type}
// Examples:
"Auth_Login_Username_input"
"Auth_Login_Submit_button"
"Home_Feed_Post_card"
"Settings_Profile_Avatar_image"

// In Compose:
Modifier.testTag("Auth_Login_Username_input")

// In XML:
android:contentDescription="@string/auth_login_username_input"
```

### Screenshot Workflow

```kotlin
class LoginTest : BaseUiTestSuite() {

    override val packageName = "com.example.app"

    @Test
    fun testSuccessfulLogin() {
        launchApp()
        screenshot(1, "app_launched")

        val loginPage = LoginPage(device).waitForReady()
        screenshot(2, "login_page_ready")

        val homePage = loginPage.login("user@test.com", "password123")
        screenshot(3, "logged_in")

        assertPageDisplayed(homePage)
    }
}
```

### Page Object Pattern

```kotlin
class LoginPage(override val device: UiDevice) : PageElement {

    override val readyMarker = "Auth_Login_Title_text"

    val usernameField: UiObject2?
        get() = device.findObject(By.res("Auth_Login_Username_input"))

    val passwordField: UiObject2?
        get() = device.findObject(By.res("Auth_Login_Password_input"))

    val loginButton: UiObject2?
        get() = device.findObject(By.res("Auth_Login_Submit_button"))

    fun login(username: String, password: String): HomePage {
        usernameField?.text = username
        passwordField?.text = password
        loginButton?.click()
        return HomePage(device).waitForReady()
    }
}
```

## Dependencies

Add to your `build.gradle.kts`:

```kotlin
// In your app's androidTest dependencies
androidTestImplementation("com.uitesttools:screenshot-kit:1.0.0")
androidTestImplementation("com.uitesttools:uitest-kit:1.0.0")

// Standard test dependencies
androidTestImplementation("androidx.test:runner:1.5.2")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
```

## Screenshot Naming Convention

Screenshots are saved in session folders with structured names:

```
/sdcard/Pictures/Screenshots/UITests/
└── Run_{session}/
    ├── Run_{session}__Test_{name}__Step_{NN}__{timestamp}__{description}.png
    └── ...

Example:
/sdcard/Pictures/Screenshots/UITests/
└── Run_20240115_143022/
    ├── Run_20240115_143022__Test_testLogin__Step_01__143025_123__initial_screen.png
    ├── Run_20240115_143022__Test_testLogin__Step_02__143026_456__filled_form.png
    └── Run_20240115_143022__Test_testLogout__Step_01__143030_789__logged_out.png
```

Each test run creates a new `Run_{session}/` folder, keeping runs separated.

After extraction with the CLI, can be further organized into:
```
screenshots/
├── Run_20240115_143022/
│   ├── Test_testLogin/
│   │   ├── Step_01_initial_screen.png
│   │   └── Step_02_filled_form.png
│   └── Test_testLogout/
│       └── Step_01_logged_out.png
```

## CLI Tools

### Extract Screenshots

```bash
# From device to local directory
./Scripts/extract-screenshots.sh ./screenshots

# With options
./Scripts/extract-screenshots.sh ./screenshots \
    --serial emulator-5554 \
    --clean

# Run tests and extract in one command
./Scripts/run-tests-and-extract.sh \
    -module app \
    -testClass com.example.LoginTest \
    -output ./screenshots
```

### Snapshot Diffs

```bash
# Compare two images
snapshotsdiff reference.png actual.png diff.png

# Batch compare failed snapshots
snapshotsdiff \
    --artifacts ./SnapshotArtifacts \
    --output ./SnapshotDiffs \
    --tests ./AppSnapshotTests
```

## Emulator & ADB Commands

### Finding Android SDK Tools

```bash
# SDK typically at:
~/Library/Android/sdk/

# Key binaries:
~/Library/Android/sdk/emulator/emulator
~/Library/Android/sdk/platform-tools/adb
~/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager
```

### Emulator Management

```bash
# List available AVDs (Android Virtual Devices)
emulator -list-avds

# Start emulator (with GUI)
emulator -avd Pixel_9_Pro_XL

# Start emulator headless (no window, for CI)
emulator -avd Pixel_9_Pro_XL -no-audio -no-window &

# Wait for emulator to boot
adb wait-for-device
adb shell getprop sys.boot_completed  # returns 1 when ready
```

### ADB Device Commands

```bash
# List connected devices/emulators
adb devices

# Check device is ready
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release

# Install APK
adb install app-debug.apk
adb install -r app-debug.apk  # reinstall

# Uninstall
adb uninstall com.example.app
```

### Screenshot Extraction

```bash
# Screenshots saved to device at:
/sdcard/Pictures/Screenshots/UITests/

# List screenshots on device
adb shell "ls -la /sdcard/Pictures/Screenshots/UITests/"

# Pull all screenshots
adb pull /sdcard/Pictures/Screenshots/UITests/ ./screenshots/

# Clean screenshots from device
adb shell "rm -rf /sdcard/Pictures/Screenshots/UITests/*"
```

### Running Tests via ADB

```bash
# Run all tests in module
./gradlew :app:connectedDebugAndroidTest

# Run specific test class
./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.example.LoginTest

# Run specific test method
./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.example.LoginTest#testSuccessfulLogin
```

## CRITICAL: Compose + UIAutomator Integration

**UIAutomator cannot see Compose `testTag` by default!**

You MUST add `testTagsAsResourceId = true` to root composable:

```kotlin
// MainActivity.kt
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }  // <-- REQUIRED!
                ) {
                    // Your app content
                }
            }
        }
    }
}
```

Without this, `device.findObject(By.res("MyTag"))` will return null!

## References

- @references/accessibility-ids.md - Test tag naming conventions
- @references/shared-identifiers.md - Sharing IDs between app and tests
- @references/page-object-pattern.md - Page Object implementation guide
- @references/allure-integration.md - Allure TestOps setup
- @references/emulator-adb.md - Emulator and ADB commands

## Assets

- @assets/UIStruct/ - Page Object templates
- @assets/TestEnvShared/ - Shared identifier templates

## Compose UI Testing

### Test Tags

```kotlin
// In your Composable
@Composable
fun LoginScreen() {
    Column {
        TextField(
            modifier = Modifier.testTag("Auth_Login_Username_input"),
            // ...
        )
        Button(
            modifier = Modifier.testTag("Auth_Login_Submit_button"),
            onClick = { /* ... */ }
        ) {
            Text("Login")
        }
    }
}
```

### Testing

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun testLoginButton() {
    composeTestRule.setContent {
        LoginScreen()
    }

    composeTestRule
        .onNodeWithTag("Auth_Login_Submit_button")
        .assertIsDisplayed()
        .performClick()
}
```

## Common Patterns

### Wait for Element

```kotlin
// UIAutomator
device.waitForResourceId("Auth_Login_Title_text", timeout = 5000)

// Compose
composeTestRule.waitForTag("Auth_Login_Title_text", timeout = 5000)

// Espresso with extension
onView(withId(R.id.loginButton))
    .waitUntilEnabled(timeout = 5000)
```

### Click at Offset (for Switches)

```kotlin
// UIAutomator - click right side of switch
element.click(Point(element.visibleBounds.right - 10, element.visibleCenter.y))

// Compose
onNodeWithTag("Settings_Notifications_Toggle_switch")
    .clickAtOffset(0.9f, 0.5f)

// Espresso
onView(withId(R.id.switch))
    .clickAtOffset(0.9f, 0.5f)
```

### Scroll and Click

```kotlin
// UIAutomator
device.scrollDownUntilFound(By.res("Settings_Advanced_button"))?.click()

// Compose
onNodeWithTag("Settings_Advanced_button")
    .scrollToAndClick()

// Espresso
onView(withId(R.id.advancedButton))
    .scrollToAndClick()
```

## Project Setup Checklist

1. [ ] Add dependencies to `build.gradle.kts`
2. [ ] Create `testTag` constants in shared module (see @assets/TestEnvShared/)
3. [ ] Set up Page Object classes (see @assets/UIStruct/)
4. [ ] Create base test class extending `BaseUiTestSuite`
5. [ ] Add test tags to all interactive elements
6. [ ] Configure Allure (optional, see @references/allure-integration.md)
7. [ ] Set up CI screenshot extraction

## Best Practices

### Test Tag Naming

- Use BEM-like pattern: `{Module}_{Screen}_{Element}_{Type}`
- Be consistent across the app
- Use underscores (Android convention)
- Include element type suffix: `_button`, `_input`, `_text`, `_image`, `_card`

### Page Objects

- One page object per screen
- Use `readyMarker` to wait for page load
- Return new page objects from navigation methods
- Keep selectors private, expose actions

### Screenshots

- Take at meaningful state changes
- Use descriptive names (snake_case)
- Number steps sequentially
- Start session in `@BeforeClass`

### Assertions

- Use `assertPageDisplayed()` for navigation verification
- Use `waitFor()` instead of `Thread.sleep()`
- Check specific elements, not just "page loaded"
