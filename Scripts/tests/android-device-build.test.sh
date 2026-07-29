#!/bin/bash
#
# android-device-build.test.sh - hermetic tests for android-device-build.sh
#
# No real device or Android SDK needed: a fake `adb` (and fake `gradlew`) is put
# on PATH / in a fake project, and its behavior is driven by FAKE_ADB_MODE.
# Focus: the MIUI INSTALL_FAILED_USER_RESTRICTED failure mode and the main
# install/run control flow.
#
# Run: ./Scripts/tests/android-device-build.test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_SH="$(cd "$SCRIPT_DIR/.." && pwd)/android-device-build.sh"

PASS=0
FAIL=0

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# --- fake adb --------------------------------------------------------------
# Modes (via FAKE_ADB_MODE): ok | miui | fail | multi
BIN="$WORK/bin"
mkdir -p "$BIN"
cat > "$BIN/adb" <<'FAKE'
#!/bin/bash
# strip leading "-s <serial>"
if [[ "${1:-}" == "-s" ]]; then shift 2; fi
sub="${1:-}"; shift || true
mode="${FAKE_ADB_MODE:-ok}"
case "$sub" in
  get-state)
    echo "device"
    ;;
  devices)
    echo "List of devices attached"
    if [[ "$mode" == "multi" ]]; then
      printf "AAAA\tdevice\nBBBB\tdevice\n"
    else
      printf "535a1632\tdevice\n"
    fi
    ;;
  shell)
    case "${1:-}" in
      getprop) echo "TestModel" ;;
      pm) echo "instrumentation:com.example.test/androidx.test.runner.AndroidJUnitRunner (target=com.example)" ;;
      am) echo "INSTRUMENTATION_STATUS_CODE: -1"; echo "OK (3 tests)" ;;
      *) echo "" ;;
    esac
    ;;
  install)
    case "$mode" in
      miui) echo "adb: failed to install ${!#}: Failure [INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]"; exit 1 ;;
      fail) echo "adb: failed to install ${!#}: Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]"; exit 1 ;;
      *)    echo "Success" ;;
    esac
    ;;
  *) echo "" ;;
esac
FAKE
chmod +x "$BIN/adb"

# --- fake android project --------------------------------------------------
PROJ="$WORK/proj"
mkdir -p "$PROJ/app/build/outputs/apk/debug" \
         "$PROJ/app/build/outputs/apk/androidTest/debug"
printf '#!/bin/bash\nexit 0\n' > "$PROJ/gradlew"
chmod +x "$PROJ/gradlew"
: > "$PROJ/app/build/outputs/apk/debug/app-debug.apk"
: > "$PROJ/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

run() {  # run <mode> -- <args...>  => sets OUT, RC
    local mode="$1"; shift
    OUT="$(cd "$PROJ" && FAKE_ADB_MODE="$mode" PATH="$BIN:$PATH" \
        bash "$BUILD_SH" "$@" 2>&1)"
    RC=$?
}

check() {  # check <label> <expected_rc> <expected_substr>
    local label="$1" want_rc="$2" want_sub="$3"
    if [[ "$RC" -eq "$want_rc" ]] && grep -qF "$want_sub" <<< "$OUT"; then
        echo "PASS: $label"; PASS=$((PASS+1))
    else
        echo "FAIL: $label (rc=$RC want=$want_rc)"; FAIL=$((FAIL+1))
        echo "----- output -----"
        # shellcheck disable=SC2001  # per-line indent prefix
        echo "$OUT" | sed 's/^/  /'
        echo "------------------"
    fi
}

# --- cases -----------------------------------------------------------------

# 1. MIUI failure -> exit 3 + guidance
run miui --no-build
check "MIUI INSTALL_FAILED_USER_RESTRICTED exits 3" 3 "INSTALL BLOCKED: INSTALL_FAILED_USER_RESTRICTED"
check "MIUI guidance mentions Install via USB" 3 "Install via USB"

# 2. Generic install failure -> exit 2 (NOT the MIUI path)
run fail --no-build
check "Generic install failure exits 2" 2 "install failed"

# 3. Install-only success -> exit 0
run ok --no-build --no-run
check "Install-only success exits 0" 0 "Install-only run complete."

# 4. Full happy path (install + instrument pass) -> exit 0
run ok --no-build
check "Full run passes exits 0" 0 "instrumentation passed"

# 5. Multiple devices, no -serial -> exit 1
run multi --no-build --no-run
check "Multiple devices requires -serial exits 1" 1 "Multiple devices attached"

# 6. --help -> exit 0
OUT="$(bash "$BUILD_SH" --help 2>&1)"; RC=$?
check "--help exits 0" 0 "physical-device lane"

# --- summary ---------------------------------------------------------------
echo
echo "=== $PASS passed, $FAIL failed ==="
[[ "$FAIL" -eq 0 ]]
