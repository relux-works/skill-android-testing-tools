#!/bin/bash

# run-tests-and-extract.sh - Run Android UI tests and extract screenshots
#
# Usage:
#   ./run-tests-and-extract.sh [OPTIONS]
#
# Two lanes:
#   1. connectedAndroidTest (default) - Gradle-managed install + run.
#      Fine for emulator/CI and most physical devices.
#   2. --manual-install                - assemble APKs, adb install -r -t,
#      then `adb shell am instrument -w`. Use this on physical devices
#      (especially MIUI/Xiaomi) where the Gradle-managed install phase fails
#      intermittently with INSTALL_FAILED_USER_RESTRICTED. This folds the
#      README-only physical lane into a first-class flag.
#
# Options:
#   -module <name>       Gradle module containing tests (default: app)
#   -testClass <name>    Specific test class to run (optional)
#   --serial <serial>    Device serial for multi-device (alias: -serial)
#   -output <path>       Output directory (default: .temp/{timestamp}_screenshots)
#   --manual-install     Manual-install lane: assemble + adb install + am instrument
#   --test-runner <comp> Instrumentation component for the manual lane,
#                        e.g. com.example.app.test/androidx.test.runner.AndroidJUnitRunner
#                        (auto-discovered via `pm list instrumentation` if omitted)
#   -clean               Delete screenshots from device after extraction
#   -h, --help           Show this help
#
# NOTE: Extraction is decoupled from the run. Screenshots are pulled even when
# the test run fails, so a red run still yields artifacts to inspect.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOOLKIT_DIR="$(dirname "$SCRIPT_DIR")"

# Default values
MODULE="app"
TEST_CLASS=""
SERIAL=""
OUTPUT=""
CLEAN=false
MANUAL_INSTALL=false
TEST_RUNNER=""

print_help() {
    echo "run-tests-and-extract.sh - Run Android UI tests and extract screenshots"
    echo
    echo "Usage:"
    echo "  ./run-tests-and-extract.sh [OPTIONS]"
    echo
    echo "Options:"
    echo "  -module <name>       Gradle module containing tests (default: app)"
    echo "  -testClass <name>    Specific test class to run (optional)"
    echo "  --serial <serial>    Device serial for multi-device (alias: -serial)"
    echo "  -output <path>       Output directory (default: .temp/{timestamp}_screenshots)"
    echo "  --manual-install     Manual-install lane: assemble + adb install + am instrument"
    echo "  --test-runner <comp> Instrumentation component for the manual lane"
    echo "                       (auto-discovered via 'pm list instrumentation' if omitted)"
    echo "  -clean               Delete screenshots from device after extraction"
    echo "  -h, --help           Show this help"
    echo
    echo "Examples:"
    echo "  ./run-tests-and-extract.sh"
    echo "  ./run-tests-and-extract.sh -module app -testClass LoginTest"
    echo "  ./run-tests-and-extract.sh --serial emulator-5554 -output ./screenshots"
    echo "  ./run-tests-and-extract.sh --manual-install --serial <device-serial>"
    echo
    echo "Physical device note:"
    echo "  On MIUI/Xiaomi, prefer --manual-install (preinstall + 'am instrument')"
    echo "  over the default connectedAndroidTest lane."
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -module)
            MODULE="$2"
            shift 2
            ;;
        -testClass)
            TEST_CLASS="$2"
            shift 2
            ;;
        --serial|-serial)
            SERIAL="$2"
            shift 2
            ;;
        -output)
            OUTPUT="$2"
            shift 2
            ;;
        --manual-install)
            MANUAL_INSTALL=true
            shift
            ;;
        --test-runner)
            TEST_RUNNER="$2"
            shift 2
            ;;
        -clean)
            CLEAN=true
            shift
            ;;
        -h|--help)
            print_help
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Run with --help for usage."
            exit 1
            ;;
    esac
done

# adb wrapper that scopes every call to the selected serial (if any)
adb_cmd() {
    if [[ -n "$SERIAL" ]]; then
        adb -s "$SERIAL" "$@"
    else
        adb "$@"
    fi
}

# Set default output directory
if [[ -z "$OUTPUT" ]]; then
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    OUTPUT=".temp/${TIMESTAMP}_screenshots"
fi

echo "=== Android UI Test Runner ==="
echo "Module: $MODULE"
echo "Output: $OUTPUT"
[[ -n "$TEST_CLASS" ]] && echo "Test class: $TEST_CLASS"
[[ -n "$SERIAL" ]] && echo "Device: $SERIAL"
$MANUAL_INSTALL && echo "Lane: manual-install (assemble + adb install + am instrument)" || echo "Lane: connectedAndroidTest"
echo

# Check for gradlew
if [[ ! -f "./gradlew" ]]; then
    echo "Error: gradlew not found in current directory"
    echo "Please run this script from your Android project root"
    exit 1
fi

# Propagate serial to Gradle/extract via ANDROID_SERIAL as well
[[ -n "$SERIAL" ]] && export ANDROID_SERIAL="$SERIAL"

run_connected_lane() {
    local test_cmd="./gradlew :${MODULE}:connectedAndroidTest"
    if [[ -n "$TEST_CLASS" ]]; then
        test_cmd="$test_cmd -Pandroid.testInstrumentationRunnerArguments.class=$TEST_CLASS"
    fi

    echo "Running UI tests (connectedAndroidTest)..."
    echo "> $test_cmd"
    echo

    if $test_cmd; then
        echo
        echo "Tests completed successfully!"
    else
        echo
        echo "Some tests failed, but continuing with screenshot extraction..."
    fi
}

run_manual_lane() {
    local script_dir keep_awake_script
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    keep_awake_script="$script_dir/../agents/skills/android-testing-tools/scripts/android-keep-awake.sh"
    if [[ -n "$SERIAL" ]]; then
        "$keep_awake_script" --serial "$SERIAL"
    else
        "$keep_awake_script"
    fi

    echo "Assembling app + androidTest APKs..."
    echo "> ./gradlew :${MODULE}:assembleDebug :${MODULE}:assembleAndroidTest"
    echo
    ./gradlew ":${MODULE}:assembleDebug" ":${MODULE}:assembleAndroidTest"

    # Locate the produced APKs (glob is robust to module-derived APK names)
    local app_apk test_apk
    app_apk=$(find "${MODULE}/build/outputs/apk/debug" -name '*-debug.apk' 2>/dev/null | head -1)
    test_apk=$(find "${MODULE}/build/outputs/apk/androidTest/debug" -name '*-androidTest.apk' 2>/dev/null | head -1)

    if [[ -z "$app_apk" || -z "$test_apk" ]]; then
        echo "Error: could not locate assembled APKs."
        echo "  app APK:  ${app_apk:-<not found>}"
        echo "  test APK: ${test_apk:-<not found>}"
        echo "Looked under ${MODULE}/build/outputs/apk/{debug,androidTest/debug}"
        exit 1
    fi

    echo
    echo "Installing APKs (adb install -r -t)..."
    echo "  app:  $app_apk"
    echo "  test: $test_apk"
    adb_cmd install -r -t "$app_apk"
    adb_cmd install -r -t "$test_apk"

    # Resolve the instrumentation component
    local runner="$TEST_RUNNER"
    if [[ -z "$runner" ]]; then
        echo
        echo "Auto-discovering instrumentation component (pm list instrumentation)..."
        # Format: instrumentation:com.example.app.test/androidx.test.runner.AndroidJUnitRunner (target=...)
        runner=$(adb_cmd shell pm list instrumentation 2>/dev/null \
            | sed -n 's/^instrumentation:\([^ ]*\).*/\1/p' | head -1 | tr -d '\r')
    fi

    if [[ -z "$runner" ]]; then
        echo "Error: no instrumentation component found."
        echo "Pass one explicitly, e.g.:"
        echo "  --test-runner com.example.app.test/androidx.test.runner.AndroidJUnitRunner"
        exit 1
    fi

    local instr_cmd="am instrument -w"
    [[ -n "$TEST_CLASS" ]] && instr_cmd="$instr_cmd -e class $TEST_CLASS"
    instr_cmd="$instr_cmd $runner"

    echo
    echo "Running UI tests (am instrument)..."
    echo "> adb shell $instr_cmd"
    echo
    if adb_cmd shell $instr_cmd; then
        echo
        echo "Instrumentation run finished."
    else
        echo
        echo "Instrumentation reported failures, but continuing with screenshot extraction..."
    fi
}

# Run tests via the selected lane
if $MANUAL_INSTALL; then
    run_manual_lane
else
    run_connected_lane
fi

# Extract screenshots (decoupled: runs regardless of test outcome)
echo
echo "Extracting screenshots..."

EXTRACT_CMD="java -jar ${TOOLKIT_DIR}/toolkit/extract-screenshots/build/libs/extract-screenshots.jar"

# Check if JAR exists, if not build it
if [[ ! -f "${TOOLKIT_DIR}/toolkit/extract-screenshots/build/libs/extract-screenshots.jar" ]]; then
    echo "Building extract-screenshots CLI..."
    (cd "$TOOLKIT_DIR/toolkit" && ./gradlew :extract-screenshots:jar)
fi

EXTRACT_ARGS="$OUTPUT"
[[ -n "$SERIAL" ]] && EXTRACT_ARGS="$EXTRACT_ARGS --serial $SERIAL"
[[ "$CLEAN" = true ]] && EXTRACT_ARGS="$EXTRACT_ARGS --clean"

echo "> $EXTRACT_CMD $EXTRACT_ARGS"
$EXTRACT_CMD $EXTRACT_ARGS

echo
echo "=== Done ==="
echo "Screenshots saved to: $OUTPUT"
echo
echo "MANDATORY next step: visually verify every extracted screenshot with the"
echo "Read tool (orientation, black screens, layout, all elements present)."
echo "See the android-testing-tools SKILL.md 'Screenshot Verification' gate."
