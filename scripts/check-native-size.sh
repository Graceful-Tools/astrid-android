#!/usr/bin/env bash
# The core library stays within its size budget (AATD-91).
#
#   scripts/check-native-size.sh <libastrid_android.so> <abi>
#
# Measures what ships: a copy stripped with the NDK's llvm-strip --strip-unneeded, which is what the
# app's packaging does to it (app/build.gradle.kts sets ndkVersion so it can). The symbols it drops
# go to Play in the bundle's native-debug-symbols instead. Budgets, in bytes, are
# core/native-size-budget.txt: lower one when the core shrinks; raising one is a decision.
set -euo pipefail
[[ $# -eq 2 ]] || { echo "usage: $0 <libastrid_android.so> <abi>" >&2; exit 64; }
lib="$1"; abi="$2"
root="$(cd "$(dirname "$0")/.." && pwd)"
budget_file="${NATIVE_SIZE_BUDGET_FILE:-$root/core/native-size-budget.txt}"
[[ -f "$lib" ]] || { echo "AATD-91: no such library: $lib" >&2; exit 1; }
budget="$(sed -nE "s/^$abi=([0-9]+)$/\1/p" "$budget_file" | head -1)"
[[ -n "$budget" ]] || { echo "AATD-91: $budget_file has no $abi= budget" >&2; exit 1; }

strip="${NATIVE_SIZE_STRIP:-}"
if [[ -z "$strip" ]]; then
  : "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME (source ~/dev-tools/android-env.sh)}"
  strip="$(ls "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/bin/llvm-strip 2>/dev/null | head -1)"
  [[ -x "$strip" ]] || { echo "AATD-91: no llvm-strip under $ANDROID_NDK_HOME" >&2; exit 1; }
fi
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
"$strip" --strip-unneeded -o "$work/lib.so" "$lib"
size="$(wc -c < "$work/lib.so" | tr -d ' ')"
mb() { awk -v b="$1" 'BEGIN { printf "%.2f MB", b / 1000000 }'; }
if (( size > budget )); then
  echo "AATD-91: $abi libastrid_android.so is $(mb "$size") stripped, over its budget of $(mb "$budget") by $((size - budget)) bytes" >&2
  exit 1
fi
echo "$abi libastrid_android.so: $(mb "$size") stripped (budget $(mb "$budget"))"
