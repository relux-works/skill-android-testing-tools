#!/usr/bin/env bash
#
# check-raw-tags.test.sh — fixture-based test for check-raw-tags.sh.
#
# Builds a throwaway Kotlin source tree with known-good and known-bad files,
# runs the lint, and asserts on exit codes and reported lines. No Android SDK,
# device, or Gradle needed — pure bash + grep.
#
# Usage: check-raw-tags.test.sh   (exit 0 = all pass, 1 = a case failed)

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LINT="$HERE/check-raw-tags.sh"

WORK="$(mktemp -d)"
EMPTY="$(mktemp -d)"
trap 'rm -rf "$WORK" "$EMPTY"' EXIT

PASS=0
FAIL=0
ok()  { PASS=$((PASS + 1)); printf 'PASS: %s\n' "$1"; }
bad() { FAIL=$((FAIL + 1)); printf 'FAIL: %s\n' "$1"; }

# expect_rc <wanted-rc> <label> <lint args...>
expect_rc() {
    local want="$1" label="$2"; shift 2
    local got=0
    "$LINT" "$@" >/dev/null 2>&1 || got=$?
    if [[ "$got" -eq "$want" ]]; then ok "$label"; else bad "$label (want rc=$want, got $got)"; fi
}

# --- fixtures ---------------------------------------------------------------

# Clean file: every tag resolved through a constant. Must pass.
mkdir -p "$WORK/app/src/main/kotlin/com/example/ui"
cat > "$WORK/app/src/main/kotlin/com/example/ui/LoginScreen.kt" <<'KT'
package com.example.ui
import com.example.testenv.TestTags
@Composable fun LoginScreen() {
    TextField(modifier = Modifier.testTag(TestTags.Auth.Login.USERNAME_INPUT))
    Button(modifier = Modifier.testTag(UITest.Identifier.Auth.Login.SUBMIT_BUTTON)) {}
}
KT

# Test file using constants across all three engines. Must pass.
mkdir -p "$WORK/app/src/androidTest/kotlin/com/example/tests"
cat > "$WORK/app/src/androidTest/kotlin/com/example/tests/LoginPage.kt" <<'KT'
package com.example.tests
import com.example.testenv.TestTags
class LoginPage {
    val user = device.findObject(By.res(TestTags.Auth.Login.USERNAME_INPUT))
    val title = composeRule.onNodeWithTag(TestTags.Auth.Login.TITLE)
    val submit = onView(withContentDescription(TestTags.Auth.Login.SUBMIT))
}
KT

# Dirty file: raw literals in every engine. Must be flagged (4 hits).
cat > "$WORK/app/src/androidTest/kotlin/com/example/tests/BadPage.kt" <<'KT'
package com.example.tests
class BadPage {
    val a = device.findObject(By.res("Auth_Login_Username_input"))
    val b = composeRule.onNodeWithTag("Auth_Login_Submit_button")
    val c = onView(withContentDescription("Auth_Login_Title_text"))
    val d = device.waitForResourceId("Auth_Login_Title_text", 5000)
    val fine = device.findObject(By.res(TestTags.Auth.Login.PASSWORD_INPUT))
}
KT

# Definition file: raw strings are legal here, must be excluded by default.
mkdir -p "$WORK/app/src/main/kotlin/com/example/testenv"
cat > "$WORK/app/src/main/kotlin/com/example/testenv/TestTags.kt" <<'KT'
package com.example.testenv
object TestTags {
    object Auth { object Login {
        const val USERNAME_INPUT = "Auth_Login_Username_input"
        const val SUBMIT_BUTTON = "Auth_Login_Submit_button"
    } }
}
KT

# --- cases ------------------------------------------------------------------

expect_rc 0 "clean Compose file passes" \
    --quiet "$WORK/app/src/main/kotlin/com/example/ui"

expect_rc 0 "constant-based test file passes" \
    --quiet "$WORK/app/src/androidTest/kotlin/com/example/tests/LoginPage.kt"

expect_rc 1 "raw-literal file is flagged (exit 1)" \
    --quiet "$WORK/app/src/androidTest/kotlin/com/example/tests/BadPage.kt"

# Dirty file reports exactly 4 offending lines.
COUNT=$("$LINT" "$WORK/app/src/androidTest/kotlin/com/example/tests/BadPage.kt" 2>/dev/null | grep -c 'BadPage.kt:' || true)
if [[ "$COUNT" -eq 4 ]]; then ok "reports 4 raw literals"; else bad "reports 4 raw literals (got $COUNT)"; fi

expect_rc 0 "TestTags.kt definition file is exempt" \
    --quiet "$WORK/app/src/main/kotlin/com/example/testenv/TestTags.kt"

# Full-tree scan: exit 1, and only the dirty file named.
OUT="$("$LINT" "$WORK" 2>/dev/null || true)"
RC=0; "$LINT" "$WORK" >/dev/null 2>&1 || RC=$?
if [[ $RC -eq 1 ]] \
   && ! grep -q 'LoginScreen.kt' <<<"$OUT" \
   && ! grep -q 'LoginPage.kt' <<<"$OUT" \
   && ! grep -q 'TestTags.kt' <<<"$OUT" \
   && grep -q 'BadPage.kt' <<<"$OUT"; then
    ok "full-tree scan flags only the dirty file"
else
    bad "full-tree scan flags only the dirty file (rc=$RC)"
fi

expect_rc 0 "--exclude glob suppresses a file" \
    --quiet --exclude '*BadPage.kt' "$WORK"

expect_rc 2 "empty tree returns usage error (exit 2)" \
    --quiet "$EMPTY"

# --- summary ----------------------------------------------------------------
echo
echo "check-raw-tags.test: $PASS passed, $FAIL failed"
[[ "$FAIL" -eq 0 ]]
