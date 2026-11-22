#!/usr/bin/env bash
# The design tokens are astrid-core's, vendored (AATD-85).
#
#   scripts/check-design-tokens.sh            fail if the vendored file was edited or is stale
#   scripts/check-design-tokens.sh --update   copy the pinned core's files over them (after a core bump)
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

# The checkbox's layers (register DS21, ADEV-196): core's vector drawables, stamped the same way,
# in XML comments. ASTRID_TOKENS_DRAWABLES overrides where they are vendored.
drawables="${ASTRID_TOKENS_DRAWABLES:-app/src/main/res/drawable}"
generated_drawables="design/generated/android/drawable"
art=(checkbox_box.xml checkbox_check.xml checkbox_repeat.xml)

if [[ "${1:-}" == "--update" ]]; then
  [[ -n "$core" && -f "$core/$generated" ]] || { echo "no pinned core checkout: run scripts/build-core.sh first" >&2; exit 1; }
  cp "$core/$generated" "$vendored"
  for f in "${art[@]}"; do cp "$core/$generated_drawables/$f" "$drawables/$f"; done
  echo "copied $generated and $generated_drawables from $core"
fi

# check FILE GENERATED-PATH STAMP-SED
check() {
  local file="$1" source="$2" stamp="$3"
  [[ -f "$file" ]] || { echo "missing $file" >&2; exit 1; }
  local stamped actual
  stamped="$(sed -n "2${stamp}p" "$file")"
  actual="$(tail -n +3 "$file" | shasum -a 256 | cut -d' ' -f1)"
  if [[ "$stamped" != "$actual" ]]; then
    echo "$file was edited by hand (body-sha256 $stamped, body $actual)." >&2
    echo "Change astrid-core design/ instead, bump the core, then scripts/check-design-tokens.sh --update." >&2
    exit 1
  fi
  if [[ -n "$core" && -f "$core/$source" ]]; then
    cmp -s "$core/$source" "$file" \
      || { echo "$file is not the pinned core's $source: scripts/check-design-tokens.sh --update" >&2; exit 1; }
  fi
}

check "$vendored" "$generated" 's/^\/\/ body-sha256: //'
for f in "${art[@]}"; do
  check "$drawables/$f" "$generated_drawables/$f" 's/^<!-- body-sha256: \(.*\) -->$/\1/'
done

if [[ -n "$core" && -f "$core/$generated" ]]; then
  echo "design tokens and checkbox art: the pinned core's, unedited"
else
  echo "design tokens and checkbox art: unedited (no pinned core checkout to compare with)"
fi
