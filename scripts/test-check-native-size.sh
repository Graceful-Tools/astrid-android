#!/usr/bin/env bash
# Self-test for scripts/check-native-size.sh (AATD-91). Needs no NDK: a stub stands in for llvm-strip.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
fail() { echo "✗ $*" >&2; exit 1; }
check() { NATIVE_SIZE_STRIP="$work/strip" NATIVE_SIZE_BUDGET_FILE="$work/budget" \
  "$root/scripts/check-native-size.sh" "$@"; }

# The stub "strips" by dropping everything after the first 1000 bytes, as the symbol table would go.
cat > "$work/strip" <<'SH'
#!/usr/bin/env bash
[[ "$1" == --strip-unneeded && "$2" == -o ]] || { echo "stub strip: unexpected $*" >&2; exit 2; }
head -c 1000 "$4" > "$3"
SH
chmod +x "$work/strip"
head -c 5000 /dev/zero > "$work/lib.so"
printf '# bytes\narm64-v8a=1000\nx86_64=10\n' > "$work/budget"

# AATD-91: the budget counts what ships — the stripped library, not the 5000 bytes cargo left.
out="$(check "$work/lib.so" arm64-v8a)" || fail "a library at its budget after stripping failed: $out"
grep -q 'arm64-v8a' <<<"$out" || fail "the pass line does not name the ABI: $out"

# AATD-91: over budget fails, and says by how much.
printf 'arm64-v8a=999\n' > "$work/budget"
if out="$(check "$work/lib.so" arm64-v8a 2>&1)"; then fail "a library over budget passed: $out"; fi
grep -q 'over its budget' <<<"$out" || fail "the failure does not say over budget: $out"

# AATD-91: an ABI with no budget line is an error, never a silent pass.
if out="$(check "$work/lib.so" x86_64 2>&1)"; then fail "an ABI with no budget passed: $out"; fi

# AATD-91: a missing library is an error.
if check "$work/nope.so" arm64-v8a >/dev/null 2>&1; then fail "a missing library passed"; fi

# The committed budget parses and names arm64.
grep -qE '^arm64-v8a=[0-9]+$' "$root/core/native-size-budget.txt" || fail "core/native-size-budget.txt has no arm64-v8a budget"
echo "check-native-size: ok"
