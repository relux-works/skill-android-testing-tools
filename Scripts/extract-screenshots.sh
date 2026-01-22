#!/bin/bash

# extract-screenshots.sh - Extract screenshots from Android device
#
# Wrapper script for the extract-screenshots CLI tool.
#
# Usage:
#   ./extract-screenshots.sh [OUTPUT_DIR] [OPTIONS]
#
# Options:
#   --serial <serial>   Device serial for multi-device
#   --device-path <path> Path on device (default: /sdcard/Pictures/Screenshots/UITests)
#   --clean             Delete screenshots from device after extraction
#   --no-organize       Don't organize into Run/Test/Step structure

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAR_PATH="${PROJECT_DIR}/toolkit/extract-screenshots/build/libs/extract-screenshots.jar"

# Build JAR if not exists
if [[ ! -f "$JAR_PATH" ]]; then
    echo "Building extract-screenshots CLI..."
    (cd "$PROJECT_DIR/toolkit" && ./gradlew :extract-screenshots:jar --quiet)
fi

# Run the tool
java -jar "$JAR_PATH" "$@"
