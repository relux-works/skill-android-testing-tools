# Emulator & ADB Reference

Complete guide for managing Android emulators and using ADB for UI testing.

## Android SDK Location

```bash
# macOS default location
~/Library/Android/sdk/

# Check via Android Studio or environment variable
echo $ANDROID_HOME
echo $ANDROID_SDK_ROOT

# Key tool locations
EMULATOR=~/Library/Android/sdk/emulator/emulator
ADB=~/Library/Android/sdk/platform-tools/adb
AVDMANAGER=~/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager
```

**Tip:** If tools aren't in PATH, use full paths or add to shell config:

```bash
# Add to ~/.zshrc or ~/.bashrc
export ANDROID_HOME=~/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
```

## Emulator Management

### List Available AVDs

```bash
emulator -list-avds
# Output:
# Pixel_9_Pro_XL
# Pixel_7_API_34
```

### Start Emulator

```bash
# With GUI (for development)
emulator -avd Pixel_9_Pro_XL

# Headless mode (for CI/automation)
emulator -avd Pixel_9_Pro_XL -no-audio -no-window &

# Cold boot (fresh start, slower but cleaner)
emulator -avd Pixel_9_Pro_XL -no-snapshot-load

# Quick boot (uses snapshot, faster)
emulator -avd Pixel_9_Pro_XL -no-snapshot-save
```

### Wait for Emulator to Boot

```bash
# Wait for device to be detected
adb wait-for-device

# Check boot completion (returns 1 when ready)
adb shell getprop sys.boot_completed

# Full wait script
wait_for_emulator() {
    adb wait-for-device
    while [ "$(adb shell getprop sys.boot_completed 2>/dev/null)" != "1" ]; do
        sleep 2
    done
    echo "Emulator ready"
}
```

### Stop Emulator

```bash
# Via ADB
adb emu kill

# Or kill process
pkill -f "emulator.*Pixel_9_Pro_XL"
```

### Create New AVD

```bash
# List available system images
sdkmanager --list | grep system-images

# Download system image
sdkmanager "system-images;android-34;google_apis;arm64-v8a"

# Create AVD
avdmanager create avd \
    -n "Pixel_8_API_34" \
    -k "system-images;android-34;google_apis;arm64-v8a" \
    -d "pixel_8"

# Delete AVD
avdmanager delete avd -n "Pixel_8_API_34"
```

## ADB Commands

### Device Management

```bash
# List connected devices
adb devices
# Output:
# List of devices attached
# emulator-5554    device
# R3CN90XXXXX      device  (physical device)

# Target specific device
adb -s emulator-5554 shell ...
adb -s R3CN90XXXXX install app.apk

# Set default device
export ANDROID_SERIAL=emulator-5554
```

### Device Info

```bash
# Device model
adb shell getprop ro.product.model

# Android version
adb shell getprop ro.build.version.release

# API level
adb shell getprop ro.build.version.sdk

# All properties
adb shell getprop
```

### App Management

```bash
# Install APK
adb install app-debug.apk
adb install -r app-debug.apk      # reinstall, keep data
adb install -t app-debug.apk      # allow test APKs
adb install -g app-debug.apk      # grant all permissions

# Uninstall
adb uninstall com.example.app
adb uninstall -k com.example.app  # keep data

# Clear app data
adb shell pm clear com.example.app

# List installed packages
adb shell pm list packages | grep example

# Get APK path
adb shell pm path com.example.app
```

### Launch App

```bash
# Start main activity
adb shell am start -n com.example.app/.MainActivity

# Start specific activity
adb shell am start -n com.example.app/.LoginActivity

# Start with intent extras
adb shell am start -n com.example.app/.MainActivity \
    --es "username" "test@example.com" \
    --ei "userId" 123

# Force stop
adb shell am force-stop com.example.app
```

## Screenshot Commands

### Screenshot Location on Device

```bash
# UITestKit saves screenshots to session folders:
/sdcard/Pictures/Screenshots/UITests/
└── Run_{session}/
    ├── Run_{session}__Test_{name}__Step_{NN}__{timestamp}__{description}.png
    └── ...

# Example structure:
/sdcard/Pictures/Screenshots/UITests/
├── Run_20240115_143022/
│   ├── Run_20240115_143022__Test_testLogin__Step_01__143025_123__initial.png
│   └── Run_20240115_143022__Test_testLogin__Step_02__143026_456__submitted.png
└── Run_20240115_150000/
    └── Run_20240115_150000__Test_testLogout__Step_01__150005_789__done.png
```

Each test run creates a new `Run_{session}/` folder.

### List Screenshots

```bash
# List session folders
adb shell "ls -la /sdcard/Pictures/Screenshots/UITests/"

# List screenshots in specific session
adb shell "ls -la /sdcard/Pictures/Screenshots/UITests/Run_20240115_143022/"
```

### Pull Screenshots

```bash
# Pull all sessions
adb pull /sdcard/Pictures/Screenshots/UITests/ ./screenshots/

# Pull specific session only
adb pull /sdcard/Pictures/Screenshots/UITests/Run_20240115_143022/ ./screenshots/

# Pull with specific device
adb -s emulator-5554 pull /sdcard/Pictures/Screenshots/UITests/ ./screenshots/
```

### Clean Screenshots

```bash
# Remove all sessions from device
adb shell "rm -rf /sdcard/Pictures/Screenshots/UITests/*"

# Remove specific session
adb shell "rm -rf /sdcard/Pictures/Screenshots/UITests/Run_20240115_143022"

# Remove entire folder
adb shell "rm -rf /sdcard/Pictures/Screenshots/UITests"
```

### Manual Screenshot

```bash
# Take screenshot via ADB
adb shell screencap /sdcard/screenshot.png
adb pull /sdcard/screenshot.png ./

# One-liner
adb exec-out screencap -p > screenshot.png
```

## Running Tests

### Via Gradle

```bash
# All instrumented tests
./gradlew :app:connectedDebugAndroidTest

# Specific test class
./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.example.LoginTest

# Specific test method
./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.example.LoginTest#testSuccessfulLogin

# Multiple test classes
./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.example.LoginTest,com.example.HomeTest

# With specific device
./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.example.LoginTest \
    -Pdevice=emulator-5554
```

### Via ADB Directly

```bash
# Run all tests in package
adb shell am instrument -w \
    com.example.app.test/androidx.test.runner.AndroidJUnitRunner

# Run specific class
adb shell am instrument -w \
    -e class com.example.app.LoginTest \
    com.example.app.test/androidx.test.runner.AndroidJUnitRunner

# Run specific method
adb shell am instrument -w \
    -e class com.example.app.LoginTest#testSuccessfulLogin \
    com.example.app.test/androidx.test.runner.AndroidJUnitRunner
```

## Compose + UIAutomator Integration

### The Problem

UIAutomator uses resource IDs (`android:id`) to find elements. Compose uses semantic properties, not resource IDs. By default, `testTag` is NOT exposed as a resource ID.

```kotlin
// This will NOT work by default with UIAutomator!
Button(modifier = Modifier.testTag("Login_Button")) { ... }

// UIAutomator returns null
device.findObject(By.res("Login_Button"))  // null!
```

### The Solution

Add `testTagsAsResourceId = true` to root composable's semantics:

```kotlin
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }  // <-- ADD THIS
    ) {
        // All children's testTags are now visible to UIAutomator
        LoginScreen()
    }
}
```

### Where to Add It

Best practice: Add to the root Surface/Box in MainActivity or App composable:

```kotlin
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyAppTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
```

## Complete Test Workflow

```bash
# 1. Start emulator (if not running)
emulator -avd Pixel_9_Pro_XL -no-audio -no-window &

# 2. Wait for boot
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed)" != "1" ]; do sleep 2; done

# 3. Clean old screenshots
adb shell "rm -rf /sdcard/Pictures/Screenshots/UITests/*"

# 4. Run tests
./gradlew :app:connectedDebugAndroidTest

# 5. Pull screenshots
mkdir -p ./screenshots
adb pull /sdcard/Pictures/Screenshots/UITests/ ./screenshots/

# 6. View results
open ./screenshots/
ls -la ./screenshots/UITests/

# 7. (Optional) Stop emulator
adb emu kill
```

## Troubleshooting

### Emulator Won't Start

```bash
# Check for port conflicts
lsof -i :5554

# Cold boot to reset state
emulator -avd Pixel_9_Pro_XL -no-snapshot-load

# Wipe data
emulator -avd Pixel_9_Pro_XL -wipe-data
```

### ADB Connection Issues

```bash
# Restart ADB server
adb kill-server
adb start-server

# Check USB debugging (physical devices)
adb devices  # should show "device", not "unauthorized"
```

### Tests Not Finding Elements

1. Check `testTagsAsResourceId = true` is set
2. Verify testTag values match exactly (case-sensitive)
3. Use UI Automator Viewer to inspect element hierarchy:
   ```bash
   # Launch UI Automator Viewer (from Android Studio or SDK)
   uiautomatorviewer
   ```
4. Add longer timeout:
   ```kotlin
   device.wait(Until.hasObject(By.res("MyTag")), 10000)
   ```

### Screenshots Not Appearing

1. Check storage permissions in test APK manifest
2. Verify path exists:
   ```bash
   adb shell "ls -la /sdcard/Pictures/Screenshots/"
   ```
3. Check for write errors in logcat:
   ```bash
   adb logcat | grep -i screenshot
   ```
