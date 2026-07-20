#!/usr/bin/env bash
#
# check-raw-tags.sh — "no raw tag strings" lint for Android UI tests.
#
# Extends the shared-identifiers rule ("use constants, never hardcode tags")
# into an enforceable check, the same way iOS enforces "no raw launch-arg
# strings". It flags string literals passed to any of the three tag lookup
# engines (Compose / Espresso / UIAutomator) instead of a `TestTags.*` /
# `UITest.Identifier.*` constant.
#
# A tag string that lives in exactly one place (the shared identifiers module)
# cannot drift between the app and the tests. A raw literal can, and it fails
# silently — `By.res("Auth_Lgoin_Submit_button")` just returns null.
#
# Usage:
#   check-raw-tags.sh [PATH ...]                 # scan given paths (default: .)
#   check-raw-tags.sh --exclude <glob> [PATH]    # add an ignore glob (repeatable)
#   check-raw-tags.sh --quiet                    # only print the summary line
#   check-raw-tags.sh -h | --help
#
# Exit codes:
#   0  no raw tag literals found
#   1  raw tag literals found (prints file:line: offending call)
#   2  bad usage / no files scanned
#
# The identifier definition files themselves are where the raw strings legally
# live, so they are excluded by default: any file whose name matches
#   *TestTags*.kt  *TestArgs*.kt  *TestConsts*.kt  *TestIds*.kt
# or that sits under a  .../testenv/  or  .../testids/  directory.
# Add more with --exclude.

set -euo pipefail

QUIET=0
declare -a PATHS=()
declare -a EXTRA_EXCLUDES=()

usage() {
    sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help) usage 0 ;;
        --quiet) QUIET=1; shift ;;
        --exclude)
            [[ $# -ge 2 ]] || { echo "check-raw-tags: --exclude needs a glob" >&2; exit 2; }
            EXTRA_EXCLUDES+=("$2"); shift 2 ;;
        --) shift; while [[ $# -gt 0 ]]; do PATHS+=("$1"); shift; done ;;
        -*) echo "check-raw-tags: unknown option '$1'" >&2; exit 2 ;;
        *) PATHS+=("$1"); shift ;;
    esac
done

[[ ${#PATHS[@]} -eq 0 ]] && PATHS=(".")

# A path component the file must NOT be under (definition modules).
is_excluded() {
    local f="$1"
    case "$f" in
        *TestTags*.kt|*TestArgs*.kt|*TestConsts*.kt|*TestIds*.kt) return 0 ;;
        */testenv/*|*/testids/*) return 0 ;;
    esac
    local g
    for g in "${EXTRA_EXCLUDES[@]:-}"; do
        [[ -n "$g" ]] || continue
        # shellcheck disable=SC2053
        [[ "$f" == $g ]] && return 0
    done
    return 1
}

# The tag lookup APIs across the three engines. A raw literal as the argument
# (an opening double-quote as the first non-space token after `(`) is a hit;
# a constant reference (identifier / TestTags.* / UITest.*) is fine.
#   Compose:      testTag(  onNodeWithTag(  onAllNodesWithTag(  waitForTag(
#   UIAutomator:  By.res(   By.desc(        waitForResourceId(
#   Espresso:     withContentDescription(   withTagValue(  (value literals)
TAG_CALL_RE='(testTag|onNodeWithTag|onAllNodesWithTag|waitForTag|waitForResourceId|withContentDescription)[[:space:]]*\([[:space:]]*"'
BY_CALL_RE='By\.(res|desc)[[:space:]]*\([[:space:]]*"'

# Collect Kotlin sources.
declare -a FILES=()
for p in "${PATHS[@]}"; do
    if [[ -f "$p" ]]; then
        [[ "$p" == *.kt ]] && FILES+=("$p")
    elif [[ -d "$p" ]]; then
        while IFS= read -r f; do FILES+=("$f"); done \
            < <(find "$p" -type f -name '*.kt' 2>/dev/null)
    else
        echo "check-raw-tags: no such path '$p'" >&2; exit 2
    fi
done

if [[ ${#FILES[@]} -eq 0 ]]; then
    echo "check-raw-tags: no .kt files found under: ${PATHS[*]}" >&2
    exit 2
fi

HITS=0
SCANNED=0
for f in "${FILES[@]}"; do
    is_excluded "$f" && continue
    SCANNED=$((SCANNED + 1))
    # grep -nE returns 1 when nothing matches; don't let set -e kill us.
    while IFS= read -r line; do
        [[ -z "$line" ]] && continue
        HITS=$((HITS + 1))
        [[ "$QUIET" -eq 0 ]] && echo "$f:$line"
    done < <(grep -nE "$TAG_CALL_RE|$BY_CALL_RE" "$f" 2>/dev/null || true)
done

if [[ "$HITS" -gt 0 ]]; then
    echo "check-raw-tags: FAIL — $HITS raw tag literal(s) in $SCANNED file(s); use TestTags/UITest constants" >&2
    exit 1
fi

[[ "$QUIET" -eq 0 ]] && echo "check-raw-tags: OK — no raw tag literals in $SCANNED file(s)"
exit 0
