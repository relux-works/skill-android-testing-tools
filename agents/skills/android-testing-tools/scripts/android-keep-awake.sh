#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-adb}"
SERIAL=""
DURATION_HOURS=24

usage() {
    cat <<'EOF'
Usage: android-keep-awake.sh [--serial SERIAL] [--adb PATH] [--duration-hours HOURS]

Keeps one Android test device awake while powered and delays screen-off plus
automatic locking. The default duration is 24 hours.
EOF
}

die() {
    echo "android-keep-awake: $*" >&2
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --serial|-serial)
            [[ $# -ge 2 ]] || die "$1 requires a value"
            SERIAL="$2"
            shift 2
            ;;
        --adb)
            [[ $# -ge 2 ]] || die "--adb requires a value"
            ADB="$2"
            shift 2
            ;;
        --duration-hours)
            [[ $# -ge 2 ]] || die "--duration-hours requires a value"
            DURATION_HOURS="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            die "unknown option: $1"
            ;;
    esac
done

[[ "$DURATION_HOURS" =~ ^[0-9]+$ ]] ||
    die "--duration-hours must be a positive integer"
DURATION_DECIMAL=$((10#$DURATION_HOURS))
(( DURATION_DECIMAL > 0 )) ||
    die "--duration-hours must be a positive integer"

if [[ "$ADB" == */* ]]; then
    [[ -x "$ADB" ]] || die "adb is not executable: $ADB"
else
    ADB="$(command -v "$ADB" || true)"
    [[ -n "$ADB" ]] || die "adb not found on PATH; pass --adb"
fi

if [[ -z "$SERIAL" ]]; then
    ONLINE=()
    while IFS= read -r device; do
        [[ -n "$device" ]] && ONLINE+=("$device")
    done < <("$ADB" devices 2>/dev/null | awk 'NR > 1 && $2 == "device" { print $1 }')
    if [[ ${#ONLINE[@]} -eq 0 ]]; then
        die "no authorized Android device is online"
    fi
    if [[ ${#ONLINE[@]} -gt 1 ]]; then
        die "multiple devices are online; pass --serial"
    fi
    SERIAL="${ONLINE[0]}"
fi

adbx() {
    "$ADB" -s "$SERIAL" "$@"
}

[[ "$(adbx get-state 2>/dev/null | tr -d '\r')" == "device" ]] ||
    die "device is not online or authorized: $SERIAL"

DURATION_MS=$((DURATION_DECIMAL * 60 * 60 * 1000))

# 7 = AC | USB | wireless. MIUI/HyperOS may report a USB cable as AC power,
# so enabling only the USB bit is not reliable enough for unattended tests.
adbx shell settings put global stay_on_while_plugged_in 7
adbx shell settings put system screen_off_timeout "$DURATION_MS"
adbx shell settings put secure lock_screen_lock_after_timeout "$DURATION_MS"
adbx shell svc power stayon true
adbx shell input keyevent KEYCODE_WAKEUP

echo "android-keep-awake: configured $SERIAL for ${DURATION_HOURS}h while powered"
