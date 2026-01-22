#!/bin/bash

# run-tests-and-extract.sh - Run Android UI tests and extract screenshots
#
# Usage:
#   ./run-tests-and-extract.sh [OPTIONS]
#
# Options:
#   -module <name>     Gradle module containing tests (default: app)
#   -testClass <name>  Specific test class to run (optional)
#   -serial <serial>   Device serial for multi-device (optional)
#   -output <path>     Output directory (default: .temp/{timestamp}_screenshots)
#   -clean             Delete screenshots from device after extraction
#   -h, --help         Show this help

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOOLKIT_DIR="$(dirname "$SCRIPT_DIR")"

# Default values
MODULE="app"
TEST_CLASS=""
SERIAL=""
OUTPUT=""
CLEAN=false

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
        -serial)
            SERIAL="$2"
            shift 2
            ;;
        -output)
            OUTPUT="$2"
            shift 2
            ;;
        -clean)
            CLEAN=true
            shift
            ;;
        -h|--help)
            echo "run-tests-and-extract.sh - Run Android UI tests and extract screenshots"
            echo
            echo "Usage:"
            echo "  ./run-tests-and-extract.sh [OPTIONS]"
            echo
            echo "Options:"
            echo "  -module <name>     Gradle module containing tests (default: app)"
            echo "  -testClass <name>  Specific test class to run (optional)"
            echo "  -serial <serial>   Device serial for multi-device (optional)"
            echo "  -output <path>     Output directory (default: .temp/{timestamp}_screenshots)"
            echo "  -clean             Delete screenshots from device after extraction"
            echo "  -h, --help         Show this help"
            echo
            echo "Examples:"
            echo "  ./run-tests-and-extract.sh"
            echo "  ./run-tests-and-extract.sh -module app -testClass LoginTest"
            echo "  ./run-tests-and-extract.sh -serial emulator-5554 -output ./screenshots"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

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
echo

# Check for gradlew
if [[ ! -f "./gradlew" ]]; then
    echo "Error: gradlew not found in current directory"
    echo "Please run this script from your Android project root"
    exit 1
fi

# Build test command
TEST_CMD="./gradlew :${MODULE}:connectedAndroidTest"

if [[ -n "$TEST_CLASS" ]]; then
    TEST_CMD="$TEST_CMD -Pandroid.testInstrumentationRunnerArguments.class=$TEST_CLASS"
fi

if [[ -n "$SERIAL" ]]; then
    export ANDROID_SERIAL="$SERIAL"
fi

# Run tests
echo "Running UI tests..."
echo "> $TEST_CMD"
echo

if $TEST_CMD; then
    echo
    echo "Tests completed successfully!"
else
    echo
    echo "Some tests failed, but continuing with screenshot extraction..."
fi

# Extract screenshots
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
