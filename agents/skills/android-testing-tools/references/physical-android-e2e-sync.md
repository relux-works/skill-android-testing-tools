# Physical two-device E2E marker sync (Android)

Port of the iOS two-phone E2E harness. The **reactive-orchestration principle
ports verbatim; the mechanics are all new** — `adb`, instrumentation args, and
on-device app files, not `devicectl` / `iproxy` / xctrunner sandboxes.

Use this when a scenario spans **two physical devices** that must observe each
other — e.g. device A performs an action and device B must see its effect
(peer discovery, presence, a pushed update, a call/handshake), then advance.

---

## The principle (ports verbatim from iOS)

1. **Markers over sleeps.** The scenario is a state machine advanced by
   **observable markers**, never by wall-clock `sleep`. A device writes a marker
   at a safe advance point; the peer waits for that marker to arrive.
2. **Bounded timeouts only *guard*, never *advance*.** A `waitForPeerMarker`
   timeout exists so a stuck peer fails the test instead of hanging — it never
   means "assume the peer got there".
3. **Screenshots at semantic boundaries are the source of truth.** Capture
   before/after each marker on the observing device.
4. **Keep both devices' artifacts**, task-scoped (per-device logcat, instrument
   logs, marker snapshots, `marker-bridge.log`, screenshots).
5. **Fail fast on premature process exit** with a log tail.
6. **Snapshot tests are a separate deterministic layer** — never used to
   synchronize a physical run.

---

## Two channels

Every marker moves over two complementary channels:

| Channel | Producer | Consumer | Purpose |
|---|---|---|---|
| **Log** | device logs `APP_E2E_MARKER <name>` (`Log.i`) | host greps `adb logcat` | fast host-side **sequencing/visibility**, no pull needed |
| **File** | device writes file `<name>` in the marker dir | host `adb pull` → `adb push` into peer's marker dir; peer's `waitForPeerMarker` sees it | **cross-device delivery** of the marker |

Both prefix/format constants live in one place —
`E2EMarkerFormat` (`toolkit/uitest-kit/.../e2e/E2EMarkerFormat.kt`) — and the
host `android-e2e-runner.sh` mirrors them. `LOG_PREFIX = "APP_E2E_MARKER"`,
marker dir name = `e2e-markers`.

---

## Where markers live — the scoped-storage caveat

**Do NOT** write markers to a bare `/sdcard/e2e-markers`. On **API 29+** that
needs `MANAGE_EXTERNAL_STORAGE` or a runtime storage permission and the write
fails under scoped storage.

**Do** write to the **app-specific external files dir**:

```
/sdcard/Android/data/<package>/files/e2e-markers/
```

resolved on-device with `Context.getExternalFilesDir(null)`. That directory is:

- writable by the app on API 29+ **without** any storage permission, and
- directly reachable from the host with `adb pull` / `adb push`.

`E2EMarkers.markerDirectory(context)` does exactly this, and
`android-e2e-runner.sh --package <pkg>` builds the same path
(`marker_dir_for`). This is the single most important thing a naïve iOS→Android
port gets wrong.

### Caveat: `adb` access to `Android/data` can be OEM-restricted (API 30+)

Even though the app-specific external files dir is the *correct* place to write
markers, direct host `adb pull`/`adb push` into
`/sdcard/Android/data/<pkg>/files/...` is **not universally reachable**. On
**API 30+** some OEM/vendor images (and hardened profiles) block `adb` shell
traversal into `Android/data` — `adb pull` returns `Permission denied` or an
empty listing even though the app itself can write the file fine. Stock AOSP,
Pixel, and emulators generally allow it; several Samsung/MIUI-style builds do
not. If the bridge logs show markers written on the device but the peer never
receives them, suspect this before touching the scenario logic.

Two fallbacks, in order of preference:

1. **`run-as` relay (debuggable app).** For a debug/`debuggable` build the app's
   own sandbox is reachable via `run-as`, which is *not* subject to the
   `Android/data` traversal restriction:

   ```bash
   # pull a marker out through the app sandbox
   adb -s "$A" exec-out run-as com.example.app \
       cat files/e2e-markers/action_done > action_done
   # push it into the peer's sandbox
   adb -s "$B" shell run-as com.example.app \
       sh -c 'mkdir -p files/e2e-markers && cat > files/e2e-markers/action_done' \
       < action_done
   ```

   `run-as` requires a `debuggable` build and the matching package on both
   devices — which the E2E lane already assumes.

2. **`E2EPeerListener` TCP channel.** When `adb`/file access is blocked
   entirely, drop the file channel and deliver markers over the device-side
   socket instead: `E2EPeerListener(context, port).start()` on the receiver,
   then `adb -s <serial> forward tcp:<port> tcp:<port>` and write one marker
   name per line from the host. This sidesteps the filesystem completely and is
   the robust path on locked-down images (at the cost of a `adb forward` setup
   step per device).

The default `android-e2e-runner.sh` bridge uses plain `adb pull`/`push` (channel
above works on AOSP/Pixel/emulator); promote the run-as relay or the listener
transport when a target device rejects `Android/data` access.

---

## adb mechanics (what replaces the iOS bits)

| iOS mechanic | Android analog |
|---|---|
| `devicectl device copy` between xctrunner sandboxes | `adb -s A pull <marker>` → `adb -s B push <marker>` (shared external files dir is directly pullable) |
| xctrunner sandbox split (runner ≠ app) | instrumentation process ≠ app process, but markers go to the **app** external files dir — adb-reachable, no copy dance |
| stdout `APP_E2E_MARKER` grep of `xcodebuild` log | `Log.i(TAG, "APP_E2E_MARKER <name>")` → host greps `adb -s <serial> logcat` |
| usbmux / `iproxy` TCP | `adb forward` / `adb reverse` to a device-side `ServerSocket` (`E2EPeerListener`) |
| `.xctestrun` per-device patch | per-device `adb -s <serial> shell am instrument -w -e <k> <v>` |
| device readiness (`lockState`, tunnel) | `adb wait-for-device`, `dumpsys deviceidle`, `wm`, boot-completed |

---

## Device-side API (`uitest-kit` `e2e/` package)

```kotlin
import com.uitesttools.uitest.e2e.E2EMarkers

// device A — poke test (short), advances then exits
E2EMarkers.clearMarkers(context)             // start clean
doTheAction()
E2EMarkers.writeMarker(context, "action_done")   // file + APP_E2E_MARKER log

// device B — observer test (long-running), blocks in a marker-wait loop
E2EMarkers.clearMarkers(context)
screenshot(1, "before_peer_action")
E2EMarkers.awaitPeerMarker(context, "action_done", timeoutMs = 60_000)
screenshot(2, "peer_action_observed")
assertThatEffectVisible()
E2EMarkers.writeMarker(context, "observed")  // let A/host know we saw it
```

- `writeMarker(context, name)` — writes the file **and** logs the line.
- `waitForPeerMarker(...)` / `awaitPeerMarker(...)` — guarded poll; `await`
  throws with an actionable message on timeout.
- `clearMarkers(context)` — wipe stale markers before a run.
- `markerDirectoryPath(context)` — the on-device path, handy for adb commands.

**Optional TCP transport** — `E2EPeerListener(context, port).start()` accepts
one marker name per line and writes it locally, so the host can deliver markers
over `adb forward tcp:8790 tcp:8790` instead of `adb push`. The file channel is
the recommended default; reach for the listener only when you want a persistent
low-latency channel.

### The long-running-role problem (same as iOS)

An Android instrumentation test process is torn down when the test method ends.
So:

- Keep the long-running role as a **single observer test that blocks in a
  marker-wait loop** (`awaitPeerMarker`), and
- use **separate short `am instrument` invocations** to poke transitions on the
  peer — do **not** try to hold a mode alive from a short test.
- Alternatively launch the app as a normal process (`am start`) and drive it
  with a separate UIAutomator instrumentation.

---

## Host bridge — `Scripts/android-e2e-runner.sh`

```bash
./Scripts/android-e2e-runner.sh \
  --package        com.example.app \
  --runner         com.example.app.test/androidx.test.runner.AndroidJUnitRunner \
  --device-a       <serialA> --driver-class   com.example.PokeTest \
  --device-b       <serialB> --observer-class com.example.ObserverTest \
  --output         .temp/e2e-run --timeout 300
```

What it does:

1. Clears stale markers + logcat on both devices.
2. Starts per-device `adb logcat` capture (visibility channel).
3. Starts the **observer** instrument on B (long-running), then the **driver**
   instrument on A (short poke).
4. **Reactive loop:** scans both devices' marker dirs; copies any new marker
   across to the peer (`adb pull` → `adb push`); **fails fast** if either
   instrument process exits non-zero (dumps a log tail); finishes when all
   instrument processes are done or the `--timeout` guard fires.
5. Collects artifacts under `--output`: `logcat-A.log`, `logcat-B.log`,
   `instrument-A.log`, `instrument-B.log`, `markers-A.txt`, `markers-B.txt`,
   `marker-bridge.log`, and staged marker files under `markers/`.

Helper flags for development without hardware:

- `--self-test` — runs the script's internal assertions (path construction,
  marker parsing, scoped-storage guard) and exits. No device needed.
- `--dry-run` — prints the planned adb commands and exits.
- `--adb <path>` — use a specific `adb` binary (or set `ADB=`).

Bash is the deliberate first cut (matches the existing `Scripts/`); promote to a
JVM CLI under `toolkit/` only if the copy/sequencing logic grows.

---

## Common failures

| Symptom | Cause | Fix |
|---|---|---|
| `adb: device 'X' not found` / offline | device unauthorized, cable, or asleep | `adb devices`, accept the RSA prompt, `adb wait-for-device`; wake/unlock |
| marker file never appears on device | wrote to bare `/sdcard/...` under scoped storage (API 30+) | use `getExternalFilesDir(null)` / the `e2e-markers` dir under `Android/data/<pkg>/files` |
| marker written on device but peer never receives it; `adb pull` says `Permission denied`/empty | OEM/vendor image blocks `adb` traversal into `Android/data` (API 30+) | use the `run-as` relay (debuggable build) or the `E2EPeerListener` TCP channel — see the scoped-storage caveat section |
| observer "sees" its own stale marker | markers from a previous run not cleared | `clearMarkers(context)` in setup; the runner clears on start |
| instrument exits before the peer advances | long role held by a short test; process torn down at method end | single blocking observer test + separate poke instruments (or `am start` the app) |
| host loop hangs forever | relied on a sleep / no timeout | markers advance, `--timeout` only guards; check the peer actually wrote its marker |
| logcat truncated / marker line missing | logcat ring buffer overflow | `adb logcat -c` before the run (runner does this); the **file** channel is authoritative, log is visibility only |
| MIUI/Xiaomi install refused (`INSTALL_FAILED_USER_RESTRICTED`) | vendor install restriction | preinstall debug + androidTest APKs with `adb install -r -t`, enable "install via USB" (see `references/emulator-adb.md`) |

---

## Checklist

- [ ] Markers in the app-specific external files dir, never bare `/sdcard`.
- [ ] Every advance point writes a marker (file + log); no `sleep` advances the
      scenario.
- [ ] Observer is a single long-running blocking test; pokes are separate short
      instruments.
- [ ] Screenshot before/after each observed marker.
- [ ] Both devices' artifacts collected task-scoped under `--output`.
- [ ] Timeouts guard, never advance.
