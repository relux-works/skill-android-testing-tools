#!/bin/bash

# android-e2e-runner.sh - Two-device physical E2E marker bridge (host side)
#
# Reactive orchestration, ported from the iOS two-phone E2E harness:
#   - markers over sleeps: the scenario is advanced by observable markers, not
#     wall-clock waits; the --timeout only *guards* the run.
#   - two channels: (1) each device logs "APP_E2E_MARKER <name>" for host-side
#     sequencing/visibility; (2) each device writes a marker FILE that this
#     bridge copies across to the peer via `adb pull` + `adb push`.
#   - fail fast: if either instrumentation process exits non-zero, stop and dump
#     a log tail instead of hanging.
#   - keep artifacts from BOTH devices (per-device logcat + instrument logs +
#     marker snapshots + marker-bridge.log).
#
# Scoped-storage caveat (API 29+):
#   Marker files live in the APP-SPECIFIC external files dir
#     /sdcard/Android/data/<package>/files/e2e-markers/
#   which is adb-pullable/pushable WITHOUT MANAGE_EXTERNAL_STORAGE. Never point
#   --package markers at a bare /sdcard/e2e-markers — that trips scoped storage
#   on API 30+. See references/physical-android-e2e-sync.md.
#
# Usage:
#   ./android-e2e-runner.sh \
#     --package        com.example.app \
#     --runner         com.example.app.test/androidx.test.runner.AndroidJUnitRunner \
#     --device-a       <serialA> --driver-class   com.example.PokeTest \
#     --device-b       <serialB> --observer-class com.example.ObserverTest \
#     --output         .temp/e2e-run --timeout 300
#
# Options:
#   --package <pkg>        App-under-test package (marker dir owner). Required.
#   --runner <comp>        Instrumentation component <testPkg>/<runnerClass>. Required.
#   --device-a <serial>    Serial of device A (driver / poke role). Required (live run).
#   --device-b <serial>    Serial of device B (observer / long-running role). Required (live run).
#   --driver-class <FQN>   Test class to run on device A (short poke test).
#   --observer-class <FQN> Test class to run on device B (long-running observer).
#   --output <dir>         Artifact dir (default: .temp/<timestamp>_e2e).
#   --timeout <sec>        Guard timeout for the whole run (default: 300).
#   --poll <sec>           Marker scan interval (default: 1).
#   --adb <path>           adb binary (default: adb on PATH).
#   --dry-run              Print the planned adb commands and exit.
#   --self-test            Run internal assertions (no device needed) and exit.
#   -h, --help             Show this help.

set -euo pipefail

# ----- constants (must match E2EMarkerFormat.kt) -----------------------------
LOG_PREFIX="APP_E2E_MARKER"
MARKER_DIR_NAME="e2e-markers"

# ----- defaults --------------------------------------------------------------
ADB="${ADB:-adb}"
PACKAGE=""
RUNNER=""
DEVICE_A=""
DEVICE_B=""
DRIVER_CLASS=""
OBSERVER_CLASS=""
OUTPUT=""
TIMEOUT=300
POLL=1
DRY_RUN=false
SELF_TEST=false

usage() {
    sed -n '3,60p' "$0" | sed 's/^# \{0,1\}//'
}

# ----- pure helpers (unit-testable in --self-test) ---------------------------

# marker_dir_for <package> -> app-specific external files marker dir on device.
marker_dir_for() {
    printf '/sdcard/Android/data/%s/files/%s' "$1" "$MARKER_DIR_NAME"
}

# is_reaped_slot <pid> -> true (rc 0) if the slot holds the reaped sentinel.
# The completion loop replaces a finished instrument's pid with -1. We MUST NOT
# feed -1 to `kill -0`: `kill -0 -1` targets the caller's whole process group and
# returns success, so a reaped slot would masquerade as "still alive" and the
# all_done break would never fire (B1). Skip reaped slots via this guard instead.
REAPED_SLOT=-1
is_reaped_slot() {
    [[ "$1" == "$REAPED_SLOT" ]]
}

# parse_marker <logcat-line> -> marker name on stdout, or nothing.
# Anchors on LOG_PREFIX and echoes the following whitespace-delimited token.
parse_marker() {
    awk -v p="$LOG_PREFIX" '
        {
            i = index($0, p)
            if (i == 0) next
            rest = substr($0, i + length(p))
            n = split(rest, a, /[ \t]+/)
            for (k = 1; k <= n; k++) {
                if (a[k] != "") { print a[k]; exit }
            }
        }' <<<"$1"
}

# ----- self-test -------------------------------------------------------------
run_self_test() {
    local fail=0
    assert_eq() {
        if [[ "$1" != "$2" ]]; then
            echo "FAIL: expected '$2', got '$1' ($3)"
            fail=1
        else
            echo "ok: $3"
        fi
    }

    assert_eq "$(marker_dir_for com.example.app)" \
        "/sdcard/Android/data/com.example.app/files/e2e-markers" \
        "marker_dir_for builds app-specific external files path"

    assert_eq "$LOG_PREFIX" "APP_E2E_MARKER" "log prefix matches Kotlin contract"
    assert_eq "$MARKER_DIR_NAME" "e2e-markers" "marker dir name matches Kotlin contract"

    assert_eq "$(parse_marker 'APP_E2E_MARKER peer_detected')" "peer_detected" \
        "parse_marker extracts a plain marker"
    assert_eq "$(parse_marker '07-21 12:00:00.1 1234 1234 I E2EMarker: APP_E2E_MARKER peer_stable')" \
        "peer_stable" "parse_marker tolerates logcat metadata"
    assert_eq "$(parse_marker 'APP_E2E_MARKER mode_restarted trailing junk')" "mode_restarted" \
        "parse_marker reads only first token"
    assert_eq "$(parse_marker 'nothing to see here')" "" \
        "parse_marker yields nothing without a marker"

    # scoped-storage guard: marker path must NOT be a bare /sdcard/e2e-markers
    if [[ "$(marker_dir_for com.x)" == "/sdcard/${MARKER_DIR_NAME}" ]]; then
        echo "FAIL: marker path collapsed to bare /sdcard (scoped-storage risk)"
        fail=1
    else
        echo "ok: marker path is app-scoped, not bare /sdcard"
    fi

    # ----- B1 regression: reaped-slot handling in the completion loop ---------
    # is_reaped_slot must recognize the sentinel and reject real pids.
    assert_eq "$(is_reaped_slot "$REAPED_SLOT" && echo yes || echo no)" "yes" \
        "is_reaped_slot recognizes the reaped sentinel"
    assert_eq "$(is_reaped_slot 12345 && echo yes || echo no)" "no" \
        "is_reaped_slot rejects a live pid"

    # Simulate the loop's termination decision without devices. `_live` is the set
    # of pids a mocked `kill -0` would report alive. Crucially it INCLUDES the
    # reaped sentinel to reproduce the bug: on a real shell `kill -0 -1` succeeds,
    # so if the guard were absent a reaped slot would read as alive and all_done
    # could never become true. With is_reaped_slot short-circuiting, it must.
    sim_all_done() {
        local live_csv="$1"; shift
        local all_done=true pid
        for pid in "$@"; do
            is_reaped_slot "$pid" && continue
            [[ ",$live_csv," == *",$pid,"* ]] && all_done=false
        done
        echo "$all_done"
    }

    # All slots reaped (kill -0 -1 would lie "alive") -> must still terminate.
    assert_eq "$(sim_all_done "$REAPED_SLOT" "$REAPED_SLOT" "$REAPED_SLOT")" "true" \
        "loop terminates when every slot is reaped (kill -0 -1 trap avoided)"
    # One reaped + one genuinely alive -> keep looping.
    assert_eq "$(sim_all_done "4242" "$REAPED_SLOT" 4242)" "false" \
        "loop keeps waiting while a real instrument is alive"
    # Reaped + a dead-but-unreaped pid (not in live set) -> terminate.
    assert_eq "$(sim_all_done "4242" "$REAPED_SLOT" 9999)" "true" \
        "loop terminates once the last live slot dies"

    # ----- N1: am instrument exits 0 on test failure; grep FAILURES!!! --------
    local instr_out
    instr_out="INSTRUMENTATION_STATUS: stream=
Failures: 1

FAILURES!!!
Tests run: 3,  Failures: 1"
    if grep -q 'FAILURES!!!' <<<"$instr_out"; then
        echo "ok: FAILURES!!! banner detected in instrument output (rc=0 -> failure)"
    else
        echo "FAIL: FAILURES!!! banner not detected"
        fail=1
    fi
    if grep -q 'FAILURES!!!' <<<"OK (3 tests)"; then
        echo "FAIL: FAILURES!!! false-positive on a green run"
        fail=1
    else
        echo "ok: green instrument output does not trip the FAILURES!!! guard"
    fi

    if [[ "$fail" -eq 0 ]]; then
        echo "SELF-TEST PASSED"
        return 0
    fi
    echo "SELF-TEST FAILED"
    return 1
}

# ----- adb helpers -----------------------------------------------------------
adb_dev() {
    local serial="$1"; shift
    "$ADB" -s "$serial" "$@"
}

require_device() {
    local serial="$1"
    if ! "$ADB" -s "$serial" get-state >/dev/null 2>&1; then
        echo "Error: device '$serial' is not connected/authorized (adb get-state failed)." >&2
        exit 1
    fi
}

# list markers currently present on a device's marker dir (names only).
list_markers() {
    local serial="$1" dir="$2"
    adb_dev "$serial" shell "ls -1 '$dir' 2>/dev/null" | tr -d '\r' | sed '/^$/d'
}

# copy a single marker file from one device to the other via the host.
copy_marker() {
    local name="$1" from_serial="$2" from_dir="$3" to_serial="$4" to_dir="$5" staging="$6"
    local local_file="$staging/$name"
    adb_dev "$from_serial" pull "$from_dir/$name" "$local_file" >/dev/null 2>&1 || return 1
    adb_dev "$to_serial" shell "mkdir -p '$to_dir'" >/dev/null 2>&1 || true
    adb_dev "$to_serial" push "$local_file" "$to_dir/$name" >/dev/null 2>&1 || return 1
    return 0
}

# ----- arg parsing -----------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --package)        PACKAGE="$2"; shift 2 ;;
        --runner)         RUNNER="$2"; shift 2 ;;
        --device-a)       DEVICE_A="$2"; shift 2 ;;
        --device-b)       DEVICE_B="$2"; shift 2 ;;
        --driver-class)   DRIVER_CLASS="$2"; shift 2 ;;
        --observer-class) OBSERVER_CLASS="$2"; shift 2 ;;
        --output)         OUTPUT="$2"; shift 2 ;;
        --timeout)        TIMEOUT="$2"; shift 2 ;;
        --poll)           POLL="$2"; shift 2 ;;
        --adb)            ADB="$2"; shift 2 ;;
        --dry-run)        DRY_RUN=true; shift ;;
        --self-test)      SELF_TEST=true; shift ;;
        -h|--help)        usage; exit 0 ;;
        *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
    esac
done

if [[ "$SELF_TEST" == true ]]; then
    run_self_test
    exit $?
fi

# ----- validate required args (live run) -------------------------------------
missing=()
[[ -z "$PACKAGE" ]]  && missing+=(--package)
[[ -z "$RUNNER" ]]   && missing+=(--runner)
[[ -z "$DEVICE_A" ]] && missing+=(--device-a)
[[ -z "$DEVICE_B" ]] && missing+=(--device-b)
if [[ ${#missing[@]} -gt 0 ]]; then
    echo "Error: missing required options: ${missing[*]}" >&2
    echo "Run with --help for usage, or --self-test to validate the script." >&2
    exit 1
fi

if [[ -z "$OUTPUT" ]]; then
    OUTPUT=".temp/$(date +%Y%m%d_%H%M%S)_e2e"
fi

MARKER_DIR="$(marker_dir_for "$PACKAGE")"
STAGING="$OUTPUT/markers"
BRIDGE_LOG="$OUTPUT/marker-bridge.log"

# ----- dry run ---------------------------------------------------------------
if [[ "$DRY_RUN" == true ]]; then
    echo "# Two-device E2E plan (dry-run)"
    echo "package:      $PACKAGE"
    echo "marker dir:   $MARKER_DIR   (app-specific external files dir; scoped-storage safe)"
    echo "device A:     $DEVICE_A  driver-class=${DRIVER_CLASS:-<none>}"
    echo "device B:     $DEVICE_B  observer-class=${OBSERVER_CLASS:-<none>}"
    echo "output:       $OUTPUT"
    echo "timeout:      ${TIMEOUT}s   poll: ${POLL}s"
    echo
    echo "$ADB -s $DEVICE_A shell rm -f '$MARKER_DIR'/*"
    echo "$ADB -s $DEVICE_B shell rm -f '$MARKER_DIR'/*"
    [[ -n "$OBSERVER_CLASS" ]] && \
        echo "$ADB -s $DEVICE_B shell am instrument -w -e class $OBSERVER_CLASS $RUNNER   &  # long-running observer"
    [[ -n "$DRIVER_CLASS" ]] && \
        echo "$ADB -s $DEVICE_A shell am instrument -w -e class $DRIVER_CLASS $RUNNER      &  # short poke"
    echo "# then: reactive loop copying new markers A<->B until both instrument procs finish"
    exit 0
fi

# ----- live run --------------------------------------------------------------
require_device "$DEVICE_A"
require_device "$DEVICE_B"

mkdir -p "$STAGING"
: >"$BRIDGE_LOG"

log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$BRIDGE_LOG"; }

log "package=$PACKAGE marker_dir=$MARKER_DIR"
log "device A=$DEVICE_A device B=$DEVICE_B output=$OUTPUT timeout=${TIMEOUT}s"

# Clear stale markers + logcat on both devices so a previous run cannot advance
# this scenario.
for s in "$DEVICE_A" "$DEVICE_B"; do
    adb_dev "$s" shell "mkdir -p '$MARKER_DIR'; rm -f '$MARKER_DIR'/*" >/dev/null 2>&1 || true
    adb_dev "$s" logcat -c >/dev/null 2>&1 || true
done

# Per-device logcat capture (marker visibility channel), background.
adb_dev "$DEVICE_A" logcat -v time >"$OUTPUT/logcat-A.log" 2>&1 &
LOGCAT_A_PID=$!
adb_dev "$DEVICE_B" logcat -v time >"$OUTPUT/logcat-B.log" 2>&1 &
LOGCAT_B_PID=$!

INSTR_PIDS=()
INSTR_LABELS=()
INSTR_LOGS=()

start_instrument() {
    local serial="$1" class="$2" label="$3" logfile="$4"
    [[ -z "$class" ]] && return 0
    log "starting instrument on $serial: class=$class ($label)"
    ( adb_dev "$serial" shell "am instrument -w -e class $class $RUNNER" >"$logfile" 2>&1 ) &
    INSTR_PIDS+=("$!")
    INSTR_LABELS+=("$label:$serial")
    INSTR_LOGS+=("$logfile")
}

# Observer first (long-running, blocks in a marker-wait loop on device B), then
# the driver (short poke) on device A.
start_instrument "$DEVICE_B" "$OBSERVER_CLASS" "observer" "$OUTPUT/instrument-B.log"
start_instrument "$DEVICE_A" "$DRIVER_CLASS"   "driver"   "$OUTPUT/instrument-A.log"

# shellcheck disable=SC2329  # invoked indirectly via `trap cleanup EXIT`
cleanup() {
    kill "$LOGCAT_A_PID" "$LOGCAT_B_PID" 2>/dev/null || true
    for pid in "${INSTR_PIDS[@]:-}"; do kill "$pid" 2>/dev/null || true; done
    # snapshot final marker sets for artifacts
    list_markers "$DEVICE_A" "$MARKER_DIR" >"$OUTPUT/markers-A.txt" 2>/dev/null || true
    list_markers "$DEVICE_B" "$MARKER_DIR" >"$OUTPUT/markers-B.txt" 2>/dev/null || true
}
trap cleanup EXIT

declare -A copied_to_b=()
declare -A copied_to_a=()

deadline=$(( $(date +%s) + TIMEOUT ))
exit_code=0

# Reactive loop: copy any new marker across to the peer; fail fast if an
# instrument process exited non-zero; stop when all instrument procs finished or
# the guard timeout fires.
while :; do
    now=$(date +%s)
    if (( now >= deadline )); then
        log "TIMEOUT after ${TIMEOUT}s — markers did not converge"
        echo "--- instrument-A tail ---"; tail -n 20 "$OUTPUT/instrument-A.log" 2>/dev/null || true
        echo "--- instrument-B tail ---"; tail -n 20 "$OUTPUT/instrument-B.log" 2>/dev/null || true
        exit_code=1
        break
    fi

    # A -> B
    while IFS= read -r name; do
        [[ -z "$name" ]] && continue
        [[ -n "${copied_to_b[$name]:-}" ]] && continue
        if copy_marker "$name" "$DEVICE_A" "$MARKER_DIR" "$DEVICE_B" "$MARKER_DIR" "$STAGING"; then
            copied_to_b[$name]=1
            log "marker A->B: $name"
        fi
    done < <(list_markers "$DEVICE_A" "$MARKER_DIR")

    # B -> A
    while IFS= read -r name; do
        [[ -z "$name" ]] && continue
        [[ -n "${copied_to_a[$name]:-}" ]] && continue
        if copy_marker "$name" "$DEVICE_B" "$MARKER_DIR" "$DEVICE_A" "$MARKER_DIR" "$STAGING"; then
            copied_to_a[$name]=1
            log "marker B->A: $name"
        fi
    done < <(list_markers "$DEVICE_B" "$MARKER_DIR")

    # Fail fast on a dead instrument; finish when all are done.
    all_done=true
    for i in "${!INSTR_PIDS[@]}"; do
        pid="${INSTR_PIDS[$i]}"
        # Skip already-reaped slots FIRST — never let -1 reach kill -0 (B1).
        is_reaped_slot "$pid" && continue
        label="${INSTR_LABELS[$i]}"
        logfile="${INSTR_LOGS[$i]}"
        if kill -0 "$pid" 2>/dev/null; then
            all_done=false
        else
            wait "$pid"; rc=$?
            # `am instrument` exits 0 even when tests FAIL; the failure only shows
            # as a "FAILURES!!!" banner in its output. Promote that to rc=1 so
            # fail-fast does not miss a red run (N1).
            if [[ "$rc" -eq 0 ]] && grep -q 'FAILURES!!!' "$logfile" 2>/dev/null; then
                log "instrument '$label' exited 0 but reported FAILURES!!! — treating as failure"
                rc=1
            fi
            if [[ "$rc" -ne 0 ]]; then
                log "FAIL FAST: instrument '$label' exited rc=$rc"
                echo "--- $label tail ---"; tail -n 30 "$logfile" 2>/dev/null || true
                exit_code=1
                break 2
            fi
            # mark as reaped so we don't wait twice
            INSTR_PIDS[i]="$REAPED_SLOT"
        fi
    done
    $all_done && { log "all instrument processes finished"; break; }

    sleep "$POLL"
done

log "run complete rc=$exit_code — artifacts in $OUTPUT"
exit "$exit_code"
