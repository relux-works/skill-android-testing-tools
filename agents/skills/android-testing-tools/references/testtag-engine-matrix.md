# testTag → Engine Lookup Matrix (3 engines, one tag)

iOS has **one** identity API: `.accessibilityIdentifier(id)`. Android has **three
lookup engines** — Compose UI Test, Espresso, and UIAutomator — that resolve the
**same BEM tag** through different mechanisms. This is the single biggest *shape*
difference from iOS and the #1 source of "my selector returns null". Learn the
matrix, apply the `testTagsAsResourceId` gotcha, and route every tag through a
`TestTags` / `UITest.Identifier` constant.

The tag string itself never changes — `Auth_Login_Submit_button` is the same
across all three engines. What changes is *how you apply it in the UI* and *how
you query it in the test*.

---

## 1. The matrix

| UI layer | Apply the tag (production code) | Query it (test code) | Engine |
|---|---|---|---|
| **Compose** (in-process) | `Modifier.testTag(TestTags…SUBMIT_BUTTON)` | `composeRule.onNodeWithTag(TestTags…SUBMIT_BUTTON)` | Compose UI Test |
| **Compose** (cross-process) | same `testTag` **+** root `Modifier.semantics { testTagsAsResourceId = true }` | `device.findObject(By.res(TestTags…SUBMIT_BUTTON))` | UIAutomator |
| **View / XML** | `android:id` / `contentDescription` / `android:tag` | `onView(withId(...))` · `onView(withContentDescription(tag))` · `onView(withTagValue(...))` | Espresso |
| **View / XML** (cross-process) | `android:id` (becomes the resource-id) / `contentDescription` | `device.findObject(By.res("id"))` · `By.desc(tag)` | UIAutomator |

**Rule of thumb for picking an engine:**

- **In-process, pure-Compose assertion** → Compose UI Test (`onNodeWithTag`). Fast,
  synchronizes with recomposition automatically.
- **In-process View/XML assertion** → Espresso (`onView`). Synchronizes with the
  main-thread message queue.
- **Cross-process / whole-app / physical-device / screenshot lane** → UIAutomator
  (`device.findObject(By.res(...))`). This is the default engine for the Page
  Object + screenshot harness because it works identically on emulator and
  physical device and can see across activities/processes.

The Page Object harness in this skill defaults to **UIAutomator + `By.res`** so
one `PageManager` drives emulator and physical device the same way. Compose UI
Test rules remain available as a second mode for pure-Compose unit-ish tests.

---

## 2. CRITICAL gotcha: `testTagsAsResourceId`

**UIAutomator cannot see a Compose `testTag` unless the root composable opts in.**

```kotlin
// MainActivity.kt (or your top-level composable)
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTagsAsResourceId

@OptIn(ExperimentalComposeUiApi::class)
setContent {
    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }   // <-- REQUIRED for By.res()
        ) {
            AppContent()
        }
    }
}
```

Without `testTagsAsResourceId = true`:

- `composeRule.onNodeWithTag(tag)` — **still works** (Compose reads `testTag` from
  the semantics tree directly).
- `device.findObject(By.res(tag))` — **silently returns `null`**. No error, no
  warning. Your `waitForAppear()` just times out.

This has **no iOS analog** — on iOS one API feeds both the in-process and the
cross-process (XCUITest) lookup. On Android the Compose `testTag` lives in the
semantics tree, and only `testTagsAsResourceId = true` mirrors it into the
resource-id namespace that UIAutomator's `By.res` reads.

**Set it once on the root, not per-element.** It propagates to every descendant's
`testTag`. Setting it per-composable is redundant and error-prone.

---

## 3. Why one tag, never a raw literal

The BEM tag string must live in exactly one place — the shared
`TestTags` / `UITest.Identifier` object (see @references/shared-identifiers.md).
The exact string the app tags a view with is the exact string the test queries;
sharing a compile-time constant makes that coupling unbreakable.

```kotlin
// app + test both import the same constant — no drift possible
Modifier.testTag(TestTags.Auth.Login.SUBMIT_BUTTON)   // production
device.findObject(By.res(TestTags.Auth.Login.SUBMIT_BUTTON))  // test
```

A raw literal defeats this. `By.res("Auth_Lgoin_Submit_button")` (typo) compiles
fine and returns `null` at runtime — the exact silent failure the gotcha above
produces, but self-inflicted. This is the direct analog of iOS's "no raw
launch-arg strings" rule, applied to tags.

### Enforcement — the no-raw-tag lint

`scripts/check-raw-tags.sh` flags any string literal passed to a tag lookup API
instead of a constant. Run it in CI or ad hoc:

```bash
# scan a source tree (exit 1 if any raw tag literal is found)
agents/skills/android-testing-tools/scripts/check-raw-tags.sh app/src

# only the summary line
check-raw-tags.sh --quiet app/src

# ignore extra paths (repeatable)
check-raw-tags.sh --exclude '*/generated/*' app/src
```

It catches literals across all three engines:
`testTag("…")`, `onNodeWithTag("…")`, `onAllNodesWithTag("…")`,
`By.res("…")`, `By.desc("…")`, `waitForResourceId("…")`,
`withContentDescription("…")`, `waitForTag("…")`.

The identifier **definition** files are exempt by default (that is where the raw
strings legitimately live): any `*TestTags*.kt`, `*TestArgs*.kt`,
`*TestConsts*.kt`, `*TestIds*.kt`, or a file under a `.../testenv/` or
`.../testids/` directory.

Equivalent one-liner if you cannot ship the script:

```bash
grep -rnE '(testTag|onNodeWithTag|By\.(res|desc)|withContentDescription|waitForResourceId)[[:space:]]*\([[:space:]]*"' \
  app/src --include='*.kt' | grep -v 'TestTags.kt\|TestArgs.kt'
```

The lint is a self-test-covered utility (`scripts/check-raw-tags.test.sh`), not a
hard gate — adopt it in CI when the team wants the rule enforced.

---

## 4. Troubleshooting: "my selector returns null"

Walk this in order — most failures are #1 or #2:

1. **`By.res` on a Compose tag returns null** → root is missing
   `Modifier.semantics { testTagsAsResourceId = true }`. See §2.
2. **Typo / drift** → you used a raw literal instead of the `TestTags` constant.
   Run `check-raw-tags.sh`.
3. **Wrong engine for the layer** → you queried a View with `onNodeWithTag`
   (Compose-only) or a Compose node with `withId` (View-only). Match the matrix
   row.
4. **`By.desc` vs `By.res`** → `contentDescription` is read by `By.desc`; a
   Compose `testTag` (with the opt-in) and an XML `android:id` are read by
   `By.res`. Don't cross them.
5. **Not yet on screen** → the element exists but the query ran before render.
   Use `device.wait(Until.hasObject(By.res(tag)), timeout)` /
   `waitForAppear()`, never `Thread.sleep`. See @references/page-object-pattern.md.
6. **Off-screen** → present in the tree but not laid out; scroll first
   (`device.scrollDownUntilFound(By.res(tag))`).

---

## See also

- @references/accessibility-ids.md — BEM tag naming convention
- @references/shared-identifiers.md — single-source-of-truth constants module
- @references/page-object-pattern.md — `waitForAppear` / readiness markers
