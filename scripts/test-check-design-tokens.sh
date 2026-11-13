#!/usr/bin/env bash
# scripts/check-design-tokens.sh (AATD-85): passes the vendored file, fails a hand edit, fails a
# stale copy, and --update brings a stale copy back to the core's.
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

src=app/src/main/java/com/astrid/design/AstridTokens.kt
mkdir -p "$tmp/core/design/generated/android"
cp "$src" "$tmp/core/design/generated/android/AstridTokens.kt"
cp "$src" "$tmp/vendored.kt"
run() { ASTRID_TOKENS_FILE="$tmp/vendored.kt" ASTRID_TOKENS_CORE="$tmp/core" scripts/check-design-tokens.sh "$@"; }
fail() { echo "FAIL: $*" >&2; exit 1; }

run >/dev/null || fail "the vendored file should pass"

sed 's/0xFF2563EB/0xFF3B82F6/' "$src" > "$tmp/vendored.kt"
if run >/dev/null 2>&1; then fail "a hand edit should fail"; fi

# A stale copy: a different, correctly stamped file (one line of body removed, sha recomputed).
{ head -n 1 "$src"; body="$(tail -n +3 "$src" | sed '/^enum class/d')"
  printf '// body-sha256: %s\n' "$(printf '%s\n' "$body" | shasum -a 256 | cut -d' ' -f1)"
  printf '%s\n' "$body"; } > "$tmp/vendored.kt"
ASTRID_TOKENS_FILE="$tmp/vendored.kt" ASTRID_TOKENS_CORE="$tmp/nowhere" scripts/check-design-tokens.sh >/dev/null \
  || fail "the stale copy is stamped correctly, so it passes alone"
if run >/dev/null 2>&1; then fail "a copy that is not the core's should fail"; fi

run --update >/dev/null || fail "--update should repair it"
cmp -s "$src" "$tmp/vendored.kt" || fail "--update should copy the core's file"
echo "check-design-tokens: ok"
