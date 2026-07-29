#!/bin/bash

# android-device-build.sh - Physical Android device build/install/run lane
#
# "build means install": assembles the app + androidTest APKs, installs both on
# a physical device with `adb install -r -t`, then (optionally) runs the
# instrumentation directly with `adb shell am instrument -w`.
#
# Why this exists (physical-device lane):
#   `connectedAndroidTest` re-enters the Gradle-managed install flow, which on
#   MIUI/Xiaomi fails intermittently with INSTALL_FAILED_USER_RESTRICTED. Once
#   the APKs are already installed, direct `am instrument` is much more stable.
#   This script promotes that lane from README prose to a first-class tool and
#   turns the MIUI failure into an actionable, documented error instead of an
#   opaque Gradle stack trace.
#
# Usage:
#   ./android-device-build.sh [OPTIONS]
#
# Options:
#   -module <name>      Gradle module containing the app + tests (default: app)
#   -variant <name>     Build variant to assemble/install (default: debug)
#   -serial <serial>    Target device serial. Required when >1 device attached.
#   -testClass <spec>   Restrict instrumentation to a class or class#method
#                       (passed as `-e class <spec>`). Repeatable is not needed;
#                       comma-separate multiple classes.
#   -runner <component> Instrumentation component <testPkg>/<runner>.
#                       Auto-detected from the device if omitted.
#   -app-apk <path>     Override the app APK path (skips path auto-detection).
#   -test-apk <path>    Override the androidTest APK path.
#   -e <key=value>      Extra instrumentation arg. May be repeated.
#   --no-build          Skip `./gradlew assemble*` (use existing APKs).
#   --no-install        Skip install (assume APKs already on device).
#   --no-run            Install only; do not run instrumentation.
#   -h, --help          Show this help.
#
# Exit codes:
#   0  success
#   1  usage / environment error
#   2  install failed (generic)
#   3  install failed with MIUI INSTALL_FAILED_USER_RESTRICTED (see guidance)
#   4  instrumentation reported a test failure / crash
#
# Examples:
#   ./android-device-build.sh
#   ./android-device-build.sh -serial 535a1632 -testClass com.example.LoginTest
#   ./android-device-build.sh --no-build --no-run          # just (re)install
#   ./android-device-build.sh --no-install -testClass com.example.LoginTest#happy

set -euo pipefail

# ----- defaults ------------------------------------------------------------
MODULE="app"
VARIANT="debug"
SERIAL=""
TEST_CLASS=""
RUNNER=""
APP_APK=""
TEST_APK=""
DO_BUILD=true
DO_INSTALL=true
DO_RUN=true
EXTRA_ARGS=()

usage() {
    sed -n '3,48p' "$0" | sed 's/^# \{0,1\}//'
}

# ----- arg parsing ---------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        -module)    MODULE="$2"; shift 2 ;;
        -variant)   VARIANT="$2"; shift 2 ;;
        -serial)    SERIAL="$2"; shift 2 ;;
        -testClass) TEST_CLASS="$2"; shift 2 ;;
        -runner)    RUNNER="$2"; shift 2 ;;
        -app-apk)   APP_APK="$2"; shift 2 ;;
        -test-apk)  TEST_APK="$2"; shift 2 ;;
        -e)         EXTRA_ARGS+=("$2"); shift 2 ;;
        --no-build)   DO_BUILD=false; shift ;;
        --no-install) DO_INSTALL=false; shift ;;
        --no-run)     DO_RUN=false; shift ;;
        -h|--help)  usage; exit 0 ;;
        *) echo "Unknown option: $1" >&2; echo "Run with --help for usage." >&2; exit 1 ;;
    esac
done

# ----- helpers -------------------------------------------------------------
die() { echo "Error: $*" >&2; exit 1; }

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "$1 not found on PATH"
}

adbx() {
    # adb with the selected serial (if any)
    if [[ -n "$SERIAL" ]]; then
        adb -s "$SERIAL" "$@"
    else
        adb "$@"
    fi
}

# Print MIUI-specific remediation for INSTALL_FAILED_USER_RESTRICTED and exit 3.
miui_install_restricted() {
    cat >&2 <<'GUIDE'

============================================================================
INSTALL BLOCKED: INSTALL_FAILED_USER_RESTRICTED
============================================================================
The device refused the install. This is the classic MIUI/HyperOS (Xiaomi,
Redmi, POCO) USB-install restriction, not a bug in your APK.

Fix it on the device, then re-run this script:

  1. Settings -> About phone -> tap "MIUI version" (or "OS version") 7x to
     enable Developer options.
  2. Settings -> Additional settings -> Developer options:
       - Enable "USB debugging"
       - Enable "Install via USB"
       - Enable "USB debugging (Security settings)"  (allows simulated input;
         needed for UI tests) -- this one requires a signed-in Mi Account and
         an active SIM/network on the device.
  3. Keep the device UNLOCKED and confirm any on-screen install prompt.
  4. Some builds also require turning OFF "MIUI optimization" under Developer
     options, then rebooting.

Notes:
  - "Install via USB" often re-disables itself after a while / after reboot;
    re-check it if installs start failing again.
  - Once both APKs are installed, `am instrument` (this script's --no-install
    run mode) is stable and does not hit this restriction again.
============================================================================
GUIDE
    exit 3
}

# Install a single APK, translating known failure strings into clear errors.
install_apk() {
    local apk="$1"
    [[ -f "$apk" ]] || die "APK not found: $apk"
    echo "  install -r -t $apk"
    local out status
    # Capture combined output but keep adb's real exit status.
    set +e
    out="$(adbx install -r -t "$apk" 2>&1)"
    status=$?
    set -e
    # shellcheck disable=SC2001  # per-line indent prefix; parameter expansion can't do this
    echo "$out" | sed 's/^/    /'
    if [[ $status -ne 0 ]] || echo "$out" | grep -q "INSTALL_FAILED\|Failure \["; then
        if echo "$out" | grep -q "INSTALL_FAILED_USER_RESTRICTED"; then
            miui_install_restricted
        fi
        echo "Error: install failed for $apk" >&2
        exit 2
    fi
}

# Locate an APK under the module build outputs, honoring an explicit override.
find_apk() {
    local override="$1" kind="$2"   # kind: apk (app) | androidTest
    if [[ -n "$override" ]]; then
        echo "$override"; return
    fi
    local dir base
    if [[ "$kind" == "androidTest" ]]; then
        dir="${MODULE}/build/outputs/apk/androidTest/${VARIANT}"
    else
        dir="${MODULE}/build/outputs/apk/${VARIANT}"
    fi
    # Prefer the conventional name, else first *.apk in the dir.
    base="$(find "$dir" -maxdepth 1 -name '*.apk' 2>/dev/null | sort | head -1)"
    [[ -n "$base" ]] || die "No APK found in $dir (did the assemble step run?)"
    echo "$base"
}

# ----- preflight -----------------------------------------------------------
require_cmd adb

[[ -f "./gradlew" ]] || die "gradlew not found in $(pwd). Run from your Android project root."

# Resolve/validate the target device (bash 3.2 compatible; no mapfile).
ONLINE=()
while IFS= read -r line; do
    [[ -n "$line" ]] && ONLINE+=("$line")
done < <(adb devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}')
if [[ -z "$SERIAL" ]]; then
    if [[ ${#ONLINE[@]} -eq 0 ]]; then
        die "No devices in 'device' state. Connect a device and enable USB debugging."
    elif [[ ${#ONLINE[@]} -gt 1 ]]; then
        echo "Multiple devices attached; pass -serial <serial>:" >&2
        printf '  %s\n' "${ONLINE[@]}" >&2
        exit 1
    fi
    SERIAL="${ONLINE[0]}"
fi

MODEL="$(adbx shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)"
RELEASE="$(adbx shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || true)"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEEP_AWAKE_SCRIPT="$SCRIPT_DIR/../agents/skills/android-testing-tools/scripts/android-keep-awake.sh"
"$KEEP_AWAKE_SCRIPT" --adb "$(command -v adb)" --serial "$SERIAL"

echo "=== android-device-build ==="
echo "Device : $SERIAL  ${MODEL:+($MODEL, Android $RELEASE)}"
echo "Module : $MODULE   Variant: $VARIANT"
echo

# ----- build ---------------------------------------------------------------
if $DO_BUILD; then
    echo "--- assemble ---"
    VCAP="$(tr '[:lower:]' '[:upper:]' <<< "${VARIANT:0:1}")${VARIANT:1}"
    echo "> ./gradlew :${MODULE}:assemble${VCAP} :${MODULE}:assemble${VCAP}AndroidTest"
    ./gradlew ":${MODULE}:assemble${VCAP}" ":${MODULE}:assemble${VCAP}AndroidTest"
    echo
fi

APP_APK="$(find_apk "$APP_APK" apk)"
TEST_APK="$(find_apk "$TEST_APK" androidTest)"

# ----- install -------------------------------------------------------------
if $DO_INSTALL; then
    echo "--- install ---"
    install_apk "$APP_APK"
    install_apk "$TEST_APK"
    echo
fi

# ----- run -----------------------------------------------------------------
if ! $DO_RUN; then
    echo "Install-only run complete."
    exit 0
fi

# Resolve the instrumentation component if not given.
if [[ -z "$RUNNER" ]]; then
    # `pm list instrumentation` lines look like:
    #   instrumentation:com.example.test/androidx.test.runner.AndroidJUnitRunner (target=com.example)
    RUNNER="$(adbx shell pm list instrumentation 2>/dev/null \
        | sed -e 's/\r$//' -e 's/^instrumentation://' \
        | awk '{print $1}' | head -1)"
    [[ -n "$RUNNER" ]] || die "Could not auto-detect an instrumentation component. Pass -runner <testPkg/runner>. (Is the androidTest APK installed?)"
fi

echo "--- instrument ---"
INSTR_ARGS=()
[[ -n "$TEST_CLASS" ]] && INSTR_ARGS+=(-e class "$TEST_CLASS")
for kv in "${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"}"; do
    [[ -z "$kv" ]] && continue
    INSTR_ARGS+=(-e "${kv%%=*}" "${kv#*=}")
done

echo "> adb -s $SERIAL shell am instrument -w ${INSTR_ARGS[*]:-} $RUNNER"
set +e
INSTR_OUT="$(adbx shell am instrument -w "${INSTR_ARGS[@]+"${INSTR_ARGS[@]}"}" "$RUNNER" 2>&1)"
INSTR_STATUS=$?
set -e
# shellcheck disable=SC2001  # per-line indent prefix
echo "$INSTR_OUT" | sed 's/^/  /'

# am instrument returns 0 even when tests fail; inspect the report text.
if [[ $INSTR_STATUS -ne 0 ]] \
    || echo "$INSTR_OUT" | grep -qE "FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED|Error in |shortMsg=" ; then
    echo
    echo "Instrumentation reported failures/crash." >&2
    exit 4
fi

echo
echo "=== Done: instrumentation passed ==="
