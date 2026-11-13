#!/usr/bin/env bash
# The design tokens are astrid-core's, vendored (AATD-85).
#
#   scripts/check-design-tokens.sh            fail if the vendored file was edited or is stale
#   scripts/check-design-tokens.sh --update   copy the pinned core's file over it (after a core bump)
#
# The app's palette, spacing, radius and type come from AstridTokens.kt, which astrid-core's
# `cargo xtask gen-design` writes. Its second line is the sha256 of everything after the first two
# lines, so a hand edit fails here even with no core checkout. When Cargo has the pinned core (it
# does once scripts/build-core.sh has run), the file must also be byte for byte the core's.
#
# ASTRID_TOKENS_FILE and ASTRID_TOKENS_CORE override the two paths (scripts/test-check-design-tokens.sh).
set -euo pipefail
cd "$(dirname "$0")/.."

vendored="${ASTRID_TOKENS_FILE:-app/src/main/java/com/astrid/design/AstridTokens.kt}"
generated="design/generated/android/AstridTokens.kt"

core_root() {
  if [[ -n "${ASTRID_TOKENS_CORE:-}" ]]; then echo "$ASTRID_TOKENS_CORE"; return; fi
  [[ -f core/Cargo.toml ]] || return 0
  (cd core && cargo metadata --format-version 1 --offline 2>/dev/null) | python3 -I -c '
import json, os, sys
for p in json.load(sys.stdin)["packages"]:
    if p["name"] == "astrid-core":
        print(os.path.dirname(os.path.dirname(os.path.dirname(p["manifest_path"]))))
        break
' || true
}

core="$(core_root)"

if [[ "${1:-}" == "--update" ]]; then
  [[ -n "$core" && -f "$core/$generated" ]] || { echo "no pinned core checkout: run scripts/build-core.sh first" >&2; exit 1; }
  cp "$core/$generated" "$vendored"
  echo "copied $generated from $core"
fi

[[ -f "$vendored" ]] || { echo "missing $vendored" >&2; exit 1; }
stamped="$(sed -n '2s/^\/\/ body-sha256: //p' "$vendored")"
actual="$(tail -n +3 "$vendored" | shasum -a 256 | cut -d' ' -f1)"
if [[ "$stamped" != "$actual" ]]; then
  echo "$vendored was edited by hand (body-sha256 $stamped, body $actual)." >&2
  echo "Change astrid-core design/tokens.json instead, bump the core, then scripts/check-design-tokens.sh --update." >&2
  exit 1
fi

if [[ -n "$core" && -f "$core/$generated" ]]; then
  cmp -s "$core/$generated" "$vendored" \
    || { echo "$vendored is not the pinned core's $generated: scripts/check-design-tokens.sh --update" >&2; exit 1; }
  echo "design tokens: the pinned core's, unedited"
else
  echo "design tokens: unedited (no pinned core checkout to compare with)"
fi
