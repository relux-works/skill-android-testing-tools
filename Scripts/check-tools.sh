#!/bin/bash

# check-tools.sh - Verify prerequisites for android-ui-testing-tools
#
# Checks:
#   - Java 17+
#   - Gradle
#   - Android SDK
#   - ADB
#   - Swift (for snapshotsdiff)

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "Checking prerequisites for android-ui-testing-tools..."
echo

# Track failures
FAILED=0

# Check Java
echo -n "Java: "
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [[ "$JAVA_VERSION" -ge 17 ]]; then
        echo -e "${GREEN}✓${NC} version $JAVA_VERSION"
    else
        echo -e "${YELLOW}⚠${NC} version $JAVA_VERSION (need 17+)"
        FAILED=1
    fi
else
    echo -e "${RED}✗${NC} not found"
    FAILED=1
fi

# Check Gradle
echo -n "Gradle: "
if command -v gradle &> /dev/null; then
    GRADLE_VERSION=$(gradle --version 2>/dev/null | grep "Gradle" | head -1 | awk '{print $2}')
    echo -e "${GREEN}✓${NC} version $GRADLE_VERSION"
else
    echo -e "${YELLOW}⚠${NC} not found (will use wrapper)"
fi

# Check Android SDK
echo -n "Android SDK (ANDROID_HOME): "
if [[ -n "$ANDROID_HOME" ]] && [[ -d "$ANDROID_HOME" ]]; then
    echo -e "${GREEN}✓${NC} $ANDROID_HOME"
elif [[ -n "$ANDROID_SDK_ROOT" ]] && [[ -d "$ANDROID_SDK_ROOT" ]]; then
    echo -e "${GREEN}✓${NC} $ANDROID_SDK_ROOT (ANDROID_SDK_ROOT)"
else
    echo -e "${RED}✗${NC} not set"
    FAILED=1
fi

# Check ADB
echo -n "ADB: "
if command -v adb &> /dev/null; then
    ADB_VERSION=$(adb version 2>/dev/null | head -1 | awk '{print $5}')
    echo -e "${GREEN}✓${NC} version $ADB_VERSION"
else
    echo -e "${RED}✗${NC} not found"
    FAILED=1
fi

# Check Swift (optional, for snapshotsdiff)
echo -n "Swift (optional): "
if command -v swift &> /dev/null; then
    SWIFT_VERSION=$(swift --version 2>/dev/null | head -1 | grep -o 'Swift version [0-9.]*' | awk '{print $3}')
    echo -e "${GREEN}✓${NC} version $SWIFT_VERSION"
else
    echo -e "${YELLOW}⚠${NC} not found (snapshotsdiff won't build)"
fi

# Check connected devices
echo
echo "Checking connected devices..."
if command -v adb &> /dev/null; then
    DEVICES=$(adb devices 2>/dev/null | grep -v "List of devices" | grep "device$" | wc -l | tr -d ' ')
    if [[ "$DEVICES" -gt 0 ]]; then
        echo -e "${GREEN}✓${NC} $DEVICES device(s) connected"
        adb devices 2>/dev/null | grep "device$" | while read -r line; do
            SERIAL=$(echo "$line" | awk '{print $1}')
            MODEL=$(adb -s "$SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
            VERSION=$(adb -s "$SERIAL" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
            echo "  - $SERIAL: $MODEL (Android $VERSION)"
        done
    else
        echo -e "${YELLOW}⚠${NC} No devices connected"
    fi
else
    echo -e "${YELLOW}⚠${NC} ADB not available"
fi

# Summary
echo
if [[ "$FAILED" -eq 0 ]]; then
    echo -e "${GREEN}All required tools are available!${NC}"
    exit 0
else
    echo -e "${RED}Some required tools are missing. Please install them.${NC}"
    exit 1
fi
