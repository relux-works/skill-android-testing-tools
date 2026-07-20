# Logcat Triage Reference

Runtime log triage for **instrumented** (`connectedAndroidTest` / `am instrument`)
and **physical two-device E2E** runs. When a UI test flakes, a screenshot comes
out wrong, or an E2E marker sequence stalls, the device log is the source of
truth — this reference is the minimum set of `adb logcat` / `dumpsys`
incantations you actually need, plus the `Scripts/logcat-triage.sh` helper that
bundles them.

Screenshots tell you *what* the UI looked like. Logcat tells you *why* it got
there: a crash, an ANR, a permission denial, or a marker that never fired.

## TL;DR — helper script

`Scripts/logcat-triage.sh` wraps everything below. Run it against a connected
device (`--serial` optional for a single device):

```bash
# BEFORE a run: clear the buffer so the log holds only this run
./Scripts/logcat-triage.sh clear   -s 535a1632

# DURING: follow logcat scoped to just the app process (no other-app noise)
./Scripts/logcat-triage.sh watch   -s 535a1632 -p com.example.app

# AFTER a failure: pull crash / ANR lines
./Scripts/logcat-triage.sh crash   -s 535a1632

# E2E: see the APP_E2E_MARKER sequence in order
./Scripts/logcat-triage.sh markers -s 535a1632

# Full bundle to disk (buffer + crash + ANR + dumpsys) — attach to a bug
./Scripts/logcat-triage.sh triage  -s 535a1632 -p com.example.app -o .temp/e2e-fail
```

| Command   | What it does |
|-----------|--------------|
| `clear`   | `adb logcat -c` on the main **and** crash buffers |
| `pid`     | Resolve the app pid (`pidof`, `ps` fallback) |
| `watch`   | `adb logcat --pid=<pid>` — live, app-scoped follow |
| `crash`   | Dumps the crash buffer + greps crash/ANR signatures in main |
| `markers` | Greps `APP_E2E_MARKER` lines (chronological) |
| `triage`  | Writes `logcat-main/app/crash`, `crashes-anr`, `markers`, `dumpsys-*`, `device-info` to an output dir |

The bundle is the artifact you attach to a failing task: it captures the device
state at the moment of failure, decoupled from test execution (a crashed run
still leaves it behind).

---

## The manual commands

### 1. Clear the buffer before every run

A stale buffer is the #1 cause of triage confusion — you grep a crash that
happened three runs ago. Always clear first:

```bash
adb -s 535a1632 logcat -c            # main buffer
adb -s 535a1632 logcat -b crash -c   # dedicated crash buffer
```

Do this in the test-run script right before `am instrument` / `connectedAndroidTest`.

### 2. Scope logcat to the app process

Unscoped logcat is a firehose of every process on the device. Scope it to the
app pid so you only see your app's output:

```bash
# Resolve the pid, then follow only that process
adb -s 535a1632 logcat --pid=$(adb -s 535a1632 shell pidof com.example.app)
```

Caveats:
- `pidof` needs the app to be **running** — start it (`am start` / the test)
  first, or `pidof` returns empty and `--pid=` follows nothing.
- On a restart the pid changes; re-resolve it. The helper's `watch` falls back
  to unscoped logcat when the process isn't up yet.
- On very old devices without a usable `pidof`, fall back to
  `adb shell ps | grep com.example.app` (the helper does this automatically).

Filter by tag/level instead when you don't have a pid:

```bash
adb logcat '*:E'                       # errors and fatals only
adb logcat -s AndroidRuntime:E TestRunner:I   # specific tags
```

### 3. Crashes and native aborts

Android keeps a **dedicated crash buffer** — the cleanest source for the fatal
Java/Kotlin stack trace:

```bash
adb -s 535a1632 logcat -b crash -d     # -d = dump and exit (don't follow)
```

For native aborts / signals and anything the crash buffer misses, grep the main
buffer for the usual signatures:

```bash
adb logcat -d | grep -E \
  'FATAL EXCEPTION|AndroidRuntime: |libc: Fatal signal|beginning of crash'
```

- `FATAL EXCEPTION` / `AndroidRuntime:` → uncaught JVM exception (the stack
  trace follows on the next lines).
- `libc: Fatal signal 11 (SIGSEGV)` / `>>> com.example.app <<<` → native crash;
  full tombstone is in `/data/tombstones/` (needs root or `adb bugreport`).

### 4. ANRs (Application Not Responding)

An ANR does not always crash — the app just wedges. Signatures:

```bash
adb logcat -d | grep -E 'ANR in |Input dispatching timed out|Reason: .*ANR'
```

The full ANR trace (all thread stacks — the *real* diagnostic) lives on-device:

```bash
# Modern devices: per-process ANR traces
adb shell ls /data/anr/                 # may need root
adb shell "run-as com.example.app cat ..."   # debuggable app sandbox

# Reliable path on any device — full report incl. ANR traces:
adb bugreport ./bugreport.zip
```

In UI tests an ANR usually means a test tapped and then blocked the main thread
with a sleep, or waited on a marker/dialog that never appeared. Cross-reference
with the marker sequence (below).

### 5. E2E marker triage (`APP_E2E_MARKER`)

The two-device E2E design (§8) emits `Log.i(TAG, "APP_E2E_MARKER <name>")` at
every semantic boundary, alongside the on-device marker file. The log line is
"channel 2" — the host greps it to sequence the run without pulling files. When
an E2E run stalls, the marker log tells you **exactly how far the state machine
got**:

```bash
adb -s <serial> logcat -d | grep APP_E2E_MARKER
# 01-15 14:30:00.200 I App: APP_E2E_MARKER login_done
# 01-15 14:30:02.900 I App: APP_E2E_MARKER peer_ready
# ... expected 'transfer_done' never appears → the observing device wedged here
```

Triage flow for a stalled E2E run:
1. `markers` on **both** device serials — compare how far each side advanced.
2. The last marker on the wedged device = the transition that didn't complete.
3. `crash` that device — did the app or the instrumentation process die there?
4. If neither crashed, it's a marker-bridge / timeout issue (host didn't push
   the peer's marker in, or a scoped-storage write failed on API 30+). See
   `physical-android-e2e-sync.md` for the bridge-side failure modes.

Common E2E log signatures:

| Symptom in logcat | Likely cause |
|---|---|
| markers stop mid-sequence, no crash | peer marker never arrived — host bridge / `adb pull`+`push` gap |
| `Permission denied` writing marker dir | scoped storage; marker path must be `getExternalFilesDir(...)`, not raw `/sdcard` |
| instrumentation `Process crashed` / non-zero exit | short poke test died — fail fast, tail the log |
| device drops off `adb logcat` entirely | device offline / USB reset — `adb devices`, reconnect |

### 6. Activity / process state (`dumpsys`)

When the app *looks* wrong but didn't crash — wrong screen, not foregrounded,
killed in the background — inspect the activity stack:

```bash
adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'
adb shell dumpsys activity processes  | grep com.example.app
adb shell dumpsys activity top        # detailed state of the foreground activity
```

`mResumedActivity` not being your expected screen explains a screenshot that
captured the wrong UI. A missing process line means the app was killed (OOM /
background restriction) — check `logcat` for `lowmemorykiller` / `am_kill`.

---

## Where this fits in a run

```bash
SERIAL=535a1632
PKG=com.example.app

# 1. clean slate
./Scripts/logcat-triage.sh clear -s "$SERIAL"

# 2. install + run (physical lane — see README "Real Device Execution")
adb -s "$SERIAL" install -r -t app-debug.apk
adb -s "$SERIAL" install -r -t app-debug-androidTest.apk
adb -s "$SERIAL" shell am instrument -w \
  -e class com.example.app.LoginTest \
  com.example.app.test/androidx.test.runner.AndroidJUnitRunner

# 3. on failure — capture the bundle, attach to the task
./Scripts/logcat-triage.sh triage -s "$SERIAL" -p "$PKG" -o .temp/login-fail

# 4. pull screenshots (independent of the log bundle)
./Scripts/extract-screenshots.sh ./screenshots --serial "$SERIAL"
```

Keep log capture **decoupled from test execution**: a failed run must still
leave both the logcat bundle and the screenshots behind. The `triage` command
never depends on the test process still being alive.

## Related

- `emulator-adb.md` — full ADB / emulator command reference.
- `physical-android-e2e-sync.md` — two-device marker-bridge principle and
  bridge-side failure modes (E2E context for the `markers` command).
- `README.md` → *Real Device Execution* — the physical install + `am instrument`
  lane these triage commands sit alongside.
