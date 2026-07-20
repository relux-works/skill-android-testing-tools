#!/bin/bash

# logcat-triage.sh - Triage helper for instrumented and physical E2E runs
#
# Wraps the adb logcat / dumpsys incantations you actually need when a
# connected/instrumented test or a two-device E2E run misbehaves on a device:
#   - clear the buffer before a run so the log only contains this run
#   - follow logcat scoped to the app process (no noise from other apps)
#   - pull crash / ANR / FATAL lines out of the dedicated crash buffer
#   - grep the APP_E2E_MARKER sequence emitted by the E2E marker helpers
#   - capture a full triage bundle (buffer + crash + ANR + dumpsys) to disk
#
# Usage:
#   ./logcat-triage.sh <command> [options]
#
# Commands:
#   clear      Clear the logcat buffers (run this BEFORE a test / E2E run)
#   pid        Resolve and print the app process pid
#   watch      Live-follow logcat scoped to the app process (Ctrl-C to stop)
#   crash      Print crash / ANR / FATAL lines from the crash + main buffers
#   markers    Print APP_E2E_MARKER lines with timestamps (E2E sequencing)
#   triage     Capture a full triage bundle to a directory (default action)
#
# Options:
#   -p, --pkg <package>    App package id, e.g. com.example.app
#                          Required for pid/watch/triage unless --pid is given.
#       --pid <pid>        Use an explicit pid instead of resolving from --pkg
#   -s, --serial <serial>  Target device serial (adb -s <serial>)
#   -o, --out <dir>        Output dir for `triage` (default: .temp/logcat-triage/<ts>)
#   -h, --help             Show this help
#
# Examples:
#   ./logcat-triage.sh clear -s 535a1632
#   ./logcat-triage.sh watch  -s 535a1632 -p com.example.app
#   ./logcat-triage.sh crash  -s 535a1632
#   ./logcat-triage.sh markers -s 535a1632
#   ./logcat-triage.sh triage -s 535a1632 -p com.example.app -o .temp/e2e-fail

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

MARKER_KEY="APP_E2E_MARKER"
# Signatures that mark a crash / native abort / ANR in the main buffer.
CRASH_RE='FATAL EXCEPTION|AndroidRuntime: |ANR in |beginning of crash|libc: Fatal signal|>>> .* <<<|art::Runtime'

usage() { sed -n '3,37p' "$0" | sed 's/^# \{0,1\}//'; }

# --- arg parsing -------------------------------------------------------------
COMMAND=""
PKG=""
PID=""
SERIAL=""
OUT=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        clear|pid|watch|crash|markers|triage)
            COMMAND="$1"; shift ;;
        -p|--pkg)    PKG="$2"; shift 2 ;;
        --pid)       PID="$2"; shift 2 ;;
        -s|--serial) SERIAL="$2"; shift 2 ;;
        -o|--out)    OUT="$2"; shift 2 ;;
        -h|--help)   usage; exit 0 ;;
        *) echo -e "${RED}Unknown argument: $1${NC}" >&2; usage; exit 2 ;;
    esac
done

[[ -z "$COMMAND" ]] && COMMAND="triage"

if ! command -v adb >/dev/null 2>&1; then
    echo -e "${RED}adb not found on PATH.${NC}" >&2
    exit 1
fi

# adb command prefix, honouring --serial.
ADB=(adb)
[[ -n "$SERIAL" ]] && ADB=(adb -s "$SERIAL")

# --- helpers -----------------------------------------------------------------
resolve_pid() {
    # Echoes the app pid, or empty string if not running.
    if [[ -n "$PID" ]]; then echo "$PID"; return 0; fi
    [[ -z "$PKG" ]] && return 0
    local p
    p="$("${ADB[@]}" shell pidof "$PKG" 2>/dev/null | tr -d '\r' | awk '{print $1}')"
    if [[ -z "$p" ]]; then
        # Fallback for old devices without a usable pidof.
        p="$("${ADB[@]}" shell ps 2>/dev/null | tr -d '\r' | awk -v pk="$PKG" '$NF==pk {print $2; exit}')"
    fi
    echo "$p"
}

require_pkg_or_pid() {
    if [[ -z "$PKG" && -z "$PID" ]]; then
        echo -e "${RED}This command needs --pkg <package> (or --pid <pid>).${NC}" >&2
        exit 2
    fi
}

# --- commands ----------------------------------------------------------------
cmd_clear() {
    "${ADB[@]}" logcat -c 2>/dev/null || true
    "${ADB[@]}" logcat -b crash -c 2>/dev/null || true
    echo -e "${GREEN}Cleared logcat main + crash buffers.${NC}"
}

cmd_pid() {
    require_pkg_or_pid
    local p; p="$(resolve_pid)"
    if [[ -z "$p" ]]; then
        echo -e "${YELLOW}Process for '${PKG}' is not running.${NC}" >&2
        exit 1
    fi
    echo "$p"
}

cmd_watch() {
    require_pkg_or_pid
    local p; p="$(resolve_pid)"
    if [[ -z "$p" ]]; then
        echo -e "${YELLOW}Process for '${PKG}' is not running yet. Falling back to unscoped logcat.${NC}" >&2
        exec "${ADB[@]}" logcat
    fi
    echo -e "${GREEN}Following logcat for pid ${p} (${PKG}). Ctrl-C to stop.${NC}" >&2
    exec "${ADB[@]}" logcat --pid="$p"
}

cmd_crash() {
    echo -e "${YELLOW}=== crash buffer (adb logcat -b crash -d) ===${NC}"
    "${ADB[@]}" logcat -b crash -d 2>/dev/null || true
    echo
    echo -e "${YELLOW}=== crash/ANR signatures in main buffer ===${NC}"
    "${ADB[@]}" logcat -d 2>/dev/null | grep -E "$CRASH_RE" || echo "(none found in current main buffer)"
}

cmd_markers() {
    echo -e "${YELLOW}=== ${MARKER_KEY} sequence (chronological) ===${NC}"
    "${ADB[@]}" logcat -d 2>/dev/null | grep -F "$MARKER_KEY" || echo "(no ${MARKER_KEY} lines in current buffer)"
}

cmd_triage() {
    require_pkg_or_pid
    local ts out
    ts="$(date +%Y%m%d_%H%M%S)"
    out="${OUT:-.temp/logcat-triage/${ts}}"
    mkdir -p "$out"

    local p; p="$(resolve_pid)"

    {
        echo "serial:  ${SERIAL:-<default>}"
        echo "package: ${PKG:-<none>}"
        echo "pid:     ${p:-<not running>}"
        echo "captured: $ts"
        echo "--- getprop (model / android / sdk) ---"
        "${ADB[@]}" shell getprop ro.product.model 2>/dev/null | tr -d '\r'
        "${ADB[@]}" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r'
        "${ADB[@]}" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r'
    } > "$out/device-info.txt" 2>&1 || true

    # Full main buffer, and the pid-scoped slice when the app is alive.
    "${ADB[@]}" logcat -d > "$out/logcat-main.txt" 2>/dev/null || true
    if [[ -n "$p" ]]; then
        "${ADB[@]}" logcat -d --pid="$p" > "$out/logcat-app.txt" 2>/dev/null || true
    fi

    "${ADB[@]}" logcat -b crash -d > "$out/logcat-crash.txt" 2>/dev/null || true
    grep -E "$CRASH_RE" "$out/logcat-main.txt" > "$out/crashes-anr.txt" 2>/dev/null || true
    grep -F "$MARKER_KEY" "$out/logcat-main.txt" > "$out/markers.txt" 2>/dev/null || true

    # Activity / process state — why-is-my-app-not-foreground triage.
    "${ADB[@]}" shell dumpsys activity activities > "$out/dumpsys-activities.txt" 2>/dev/null || true
    "${ADB[@]}" shell dumpsys activity processes > "$out/dumpsys-processes.txt" 2>/dev/null || true

    echo -e "${GREEN}Triage bundle written to: ${out}${NC}"
    ls -la "$out"
    local ncrash nmark
    ncrash="$(wc -l < "$out/crashes-anr.txt" 2>/dev/null | tr -d ' ')"
    nmark="$(wc -l < "$out/markers.txt" 2>/dev/null | tr -d ' ')"
    echo -e "crash/ANR lines: ${ncrash:-0}   ${MARKER_KEY} lines: ${nmark:-0}"
    if [[ "${ncrash:-0}" -gt 0 ]]; then
        echo -e "${RED}Crash/ANR signatures found — inspect crashes-anr.txt / logcat-crash.txt${NC}"
    fi
}

case "$COMMAND" in
    clear)   cmd_clear ;;
    pid)     cmd_pid ;;
    watch)   cmd_watch ;;
    crash)   cmd_crash ;;
    markers) cmd_markers ;;
    triage)  cmd_triage ;;
    *) echo -e "${RED}Unknown command: $COMMAND${NC}" >&2; usage; exit 2 ;;
esac
