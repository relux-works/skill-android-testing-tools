# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Structure

```
android-ui-testing-tools/
├── agents/skills/           # AI skill
├── Scripts/                 # Shell scripts
├── toolkit/                 # Gradle multi-module project (libraries)
│   ├── screenshot-kit/      # Android Library
│   ├── uitest-kit/          # Android Library
│   ├── extract-screenshots/ # JVM CLI
│   └── snapshotsdiff/       # Swift CLI
├── demo-app/                # Demo app (separate Gradle project)
│   └── settings.gradle.kts  # Toggle: useLocalLibs = true/false
├── buildSrc/                # Convention plugins (shared)
├── build.gradle.kts         # Root (for sourceControl consumers)
├── settings.gradle.kts      # Root (for sourceControl consumers)
├── CLAUDE.md
└── README.md
```

## Build Commands

```bash
cd toolkit
./gradlew build              # Build all modules
./gradlew build -x test      # Build without tests
./gradlew :extract-screenshots:jar  # Build extract CLI jar
```

## CLI Tools

```bash
# Extract screenshots from Android device
java -jar toolkit/extract-screenshots/build/libs/extract-screenshots.jar [OUTPUT_DIR] [OPTIONS]

# Or use the wrapper script
./Scripts/extract-screenshots.sh ./screenshots --clean

# Create visual diff between two images
swift build -c release --package-path toolkit/snapshotsdiff
./toolkit/snapshotsdiff/.build/release/snapshotsdiff <reference.png> <failed.png> <diff.png>

# Batch compare failed snapshots against references
./toolkit/snapshotsdiff/.build/release/snapshotsdiff \
  --artifacts ./SnapshotArtifacts \
  --output ./SnapshotDiffs \
  --tests ./AppSnapshotTests
```

## Scripts

All scripts are in `Scripts/` directory.

```bash
# Check prerequisites (Java, Android SDK, ADB)
./Scripts/check-tools.sh

# Run UI tests and extract screenshots (run from your Android project!)
cd /path/to/your/project
~/src/android-ui-testing-tools/Scripts/run-tests-and-extract.sh \
  -module app \
  -testClass com.example.LoginTest \
  -output .temp/screenshots

# Extract screenshots from device
./Scripts/extract-screenshots.sh ./screenshots --serial emulator-5554

# Install AI skill to a project
./Scripts/setup-project-skills.sh /path/to/your/project

# Install AI skill globally (~/.claude + ~/.codex)
./Scripts/setup-global-skills.sh
```

## Architecture

**android-ui-testing-tools** provides UI testing utilities for Android.

### Products

| Module | Type | Purpose |
|--------|------|---------|
| **screenshot-kit** | Android Library | Capture screenshots with structured naming |
| **uitest-kit** | Android Library | Page Object protocols, Espresso/UIAutomator extensions, Allure integration |
| **extract-screenshots** | JVM CLI | Extract and organize screenshots from device via ADB |
| **snapshotsdiff** | Swift CLI (macOS) | Create visual diffs between snapshot images |
| **demo-app** | Android App | Demo application showing toolkit usage |

**Important:** Only `screenshot-kit` and `uitest-kit` go into Android test targets. CLI tools are for developer machine only.

### Module Structure

**screenshot-kit** (`toolkit/screenshot-kit/src/main/kotlin/com/uitesttools/screenshot/`)
- `ScreenshotManager` - singleton managing session timestamps and screenshot naming
- `ScreenshotTestRule` - JUnit Rule for automatic session management
- `ScreenshotNaming` - parser/builder for structured filenames
- `ComposeScreenshotExtensions` - Compose UI Test screenshot helpers

**uitest-kit** (`toolkit/uitest-kit/src/main/kotlin/com/uitesttools/uitest/`)
- `pageobject/` - `PageElement`, `ComponentElement` interfaces + `BaseUiTestSuite` base class
- `extensions/` - ViewInteraction, UiDevice, Compose extensions (waitFor, clickAtOffset, etc.)
- `allure/` - `AllureTrackable` interface and `AllureSteps` helper

**extract-screenshots** (`toolkit/extract-screenshots/src/main/kotlin/com/uitesttools/extract/`)
- CLI that uses ADB to pull screenshots from device
- Parses structured names and organizes into `Run/Test/Step` folders

**snapshotsdiff** (`toolkit/snapshotsdiff/Sources/SnapshotsDiff/`)
- Swift CLI for creating visual diffs between images (macOS only)
- Batch mode: compares failed snapshots against `__Snapshots__` references

### AI Agent Skill

Skill for AI-assisted UI test development (Claude Code + Codex CLI):

```
agents/skills/android-ui-validation/  <- actual skill
.claude/skills -> ../agents/skills    <- symlink
.codex/skills -> ../agents/skills     <- symlink
```

Contents:
- `assets/TestEnvShared/` - templates for shared test tag constants
- `assets/UIStruct/` - templates for Page Object pattern implementation
- `references/` - docs on test tags, Page Objects, Allure integration

## Key Patterns

**Screenshot workflow**:
1. Use `@ClassRule` with `ScreenshotSessionRule` for session management
2. Use `@Rule` with `ScreenshotTestRule` per test
3. Capture with `screenshot(step, "description")`

**Test tag naming**: BEM-like pattern `{Module}_{Screen}_{Element}_{Type}` (e.g., `Auth_Login_Submit_button`)

**Wait for UI state**: Use `device.waitForResourceId("tag", timeout)` instead of sleep

**Compose testTag interaction**:
- Use `Modifier.testTag("Auth_Login_Submit_button")` in Compose
- Add `Modifier.semantics { testTagsAsResourceId = true }` on root for UIAutomator access

**Screenshot output**: Extracted to `{output-dir}/Run_*/Test_*/Step_*.png`

**Snapshot testing**: Use Paparazzi/Shot for automated comparison. Use `snapshotsdiff` CLI to analyze failures.

## Publishing

Two distribution methods: **sourceControl** (recommended) and **GitHub Packages**.

**Release workflow:**
1. Update version in `build.gradle.kts` (root) and `toolkit/build.gradle.kts`
2. Commit and push
3. Create git tag: `git tag 0.0.1 && git push origin 0.0.1`
4. (Optional) Create GitHub Release to trigger GitHub Packages publish

**Version must match git tag** for sourceControl to work.

## Consuming Libraries

### Option A: Gradle sourceControl (Recommended)

No tokens needed. Gradle clones repo and builds from git tag.

```kotlin
// settings.gradle.kts
sourceControl {
    gitRepository(uri("https://github.com/ivalx1s/android-ui-testing-tools.git")) {
        producesModule("com.uitesttools:screenshot-kit")
        producesModule("com.uitesttools:uitest-kit")
    }
}

// build.gradle.kts
dependencies {
    androidTestImplementation("com.uitesttools:screenshot-kit:0.0.1")
    androidTestImplementation("com.uitesttools:uitest-kit:0.0.1")
}
```

### Option B: GitHub Packages

Requires authentication token.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
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

Add to `~/.gradle/gradle.properties`:
```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN  # needs read:packages scope
```

## Publishing Configuration

- `build.gradle.kts` (root) - group/version for sourceControl
- `toolkit/build.gradle.kts` - group/version for local dev
- `toolkit/buildSrc/.../publish-android-library.gradle.kts` - GitHub Packages plugin
- `.github/workflows/publish.yml` - CI workflow for GitHub Packages

## Gradle Version Catalog

All dependencies are managed in `toolkit/gradle/libs.versions.toml`.
