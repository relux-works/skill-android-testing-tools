#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

ADB_LOG="$TEMP_DIR/adb.log"
MOCK_ADB="$TEMP_DIR/adb"

cat >"$MOCK_ADB" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"$ADB_LOG"
if [[ "$*" == *" get-state" ]]; then
    printf 'device\n'
fi
MOCK
chmod +x "$MOCK_ADB"

export ADB_LOG
"$SCRIPT_DIR/android-keep-awake.sh" \
    --adb "$MOCK_ADB" \
    --serial test-serial \
    >"$TEMP_DIR/output.log"

for expected in \
    "-s test-serial get-state" \
    "-s test-serial shell settings put global stay_on_while_plugged_in 7" \
    "-s test-serial shell settings put system screen_off_timeout 86400000" \
    "-s test-serial shell settings put secure lock_screen_lock_after_timeout 86400000" \
    "-s test-serial shell svc power stayon true" \
    "-s test-serial shell input keyevent KEYCODE_WAKEUP"
do
    grep -F -- "$expected" "$ADB_LOG" >/dev/null
done

grep -F -- "24h" "$TEMP_DIR/output.log" >/dev/null

: >"$ADB_LOG"
"$SCRIPT_DIR/android-keep-awake.sh" \
    --adb "$MOCK_ADB" \
    --serial test-serial \
    --duration-hours 08 \
    >"$TEMP_DIR/output-leading-zero.log"
grep -F -- \
    "-s test-serial shell settings put system screen_off_timeout 28800000" \
    "$ADB_LOG" >/dev/null
grep -F -- "08h" "$TEMP_DIR/output-leading-zero.log" >/dev/null

echo "android-keep-awake tests passed"
