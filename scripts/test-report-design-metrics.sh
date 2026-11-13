#!/usr/bin/env bash
# Self-test for scripts/report-design-metrics.py (AATD-86): the hand-written colour, size and copy
# counts, and the ratchet that fails the gate when one rises.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
fail() { echo "✗ $*" >&2; exit 1; }
metrics() { python3 -I "$root/scripts/report-design-metrics.py" --root "$work/tree" --budget "$work/budget.json" "$@"; }

ui="$work/tree/app/src/main/java/com/gracefultools/astrid/ui"
mkdir -p "$ui/theme" "$ui/tasks"
# The theme is the shared home: nothing in it counts.
cat > "$ui/theme/AstridColors.kt" <<'KT'
val Accent = Color(0xFF2563EB)
val Pad = 16.dp
KT
cat > "$ui/tasks/Row.kt" <<'KT'
// A comment saying Color(0xFF000000), 4.dp and "Hello there" is prose, not code.
/** "Sign out" in KDoc is prose too. */
@Composable fun Row() {
    Box(Modifier.padding(8.dp).background(Color(0xFFFF0000)).clip(RoundedCornerShape(12.dp))) {
        Text("Hello there", fontSize = 14.sp, color = Color.Red)
        Text("Hello there", color = Color.White.copy(alpha = 0.5f))
        Icon(contentDescription = "Close the sheet", tint = Color.Transparent)
        Text(stringResource(R.string.ok), modifier = Modifier.size(24.dp))
        val key = "astridModel"
        val url = "https://astrid.cc"
        val greeting = "$name is here"
    }
}
KT

# AATD-86: each count, outside ui/theme and comments.
metrics --json > "$work/counts.json" || fail "--json failed"
expect() { python3 -I -c 'import json,sys; c=json.load(open(sys.argv[1])); sys.exit(0 if c.get(sys.argv[2])==int(sys.argv[3]) else (print(f"{sys.argv[2]}={c.get(sys.argv[2])}, expected {sys.argv[3]}") or 1))' "$work/counts.json" "$1" "$2" || fail "count $1"; }
expect colorLiterals 1        # Color(0xFFFF0000); the theme's does not count
expect namedColors 2          # Color.Red, Color.White; Transparent is not a colour choice
expect dpLiterals 3           # 8.dp, 12.dp, 24.dp
expect roundedCorners 1       # RoundedCornerShape(12.dp)
expect spLiterals 1           # 14.sp
expect hardcodedCopy 3        # "Hello there" twice, "Close the sheet"; keys, URLs and templates are not copy
expect uniqueHardcodedStrings 2

# AATD-86: at the budget passes; one over fails and names the metric.
metrics --update >/dev/null || fail "--update failed"
out="$(metrics --check)" || fail "a tree at its budget failed: $out"
echo '        Text("Another sentence here")' >> "$ui/tasks/Row.kt"
if out="$(metrics --check 2>&1)"; then fail "a rise in hardcoded copy passed: $out"; fi
grep -q 'hardcodedCopy' <<<"$out" || fail "the failure does not name the metric: $out"

# AATD-86: --update never raises a budget.
metrics --update >/dev/null || true
if metrics --check >/dev/null 2>&1; then fail "--update raised a budget"; fi

# AATD-86: a fall passes and says to lower the budget.
sed -i.bak '$d' "$ui/tasks/Row.kt"; sed -i.bak 's/Color.Red/Brand/' "$ui/tasks/Row.kt"
out="$(metrics --check)" || fail "a fall failed: $out"
grep -q 'lower it' <<<"$out" || fail "a fall does not say to lower the budget: $out"

# AATD-86: a metric with no budget is an error, never a silent pass.
echo '{}' > "$work/budget.json"
if metrics --check >/dev/null 2>&1; then fail "a missing budget passed"; fi

# The committed budget covers every metric and holds today's tree.
python3 -I "$root/scripts/report-design-metrics.py" --root "$root" --check >/dev/null \
  || fail "the app is over its committed budget (scripts/design-metrics.budget.json)"
echo "report-design-metrics: ok"
