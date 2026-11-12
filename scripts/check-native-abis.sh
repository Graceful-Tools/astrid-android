#!/usr/bin/env bash
# Every ABI an APK or AAB ships native code for must carry the core library (AATD-88).
#
#   scripts/check-native-abis.sh <app.apk|app.aab> ...
#
# JNA's AAR brings libjnidispatch.so for seven ABIs; scripts/build-core.sh builds
# libastrid_android.so for two. Without app ndk.abiFilters the package advertised all seven, so Play
# would install it on a 32-bit phone where the core fails to load at startup.
set -euo pipefail
[[ $# -gt 0 ]] || { echo "usage: $0 <app.apk|app.aab> ..." >&2; exit 64; }
status=0
for pkg in "$@"; do
  [[ -f "$pkg" ]] || { echo "AATD-88: no such package: $pkg" >&2; status=1; continue; }
  # APK: lib/<abi>/x.so; AAB: base/lib/<abi>/x.so.
  entries="$(unzip -Z1 "$pkg" | grep -E '(^|/)lib/[^/]+/[^/]+\.so$' || true)"
  abis="$(sed -E 's#^(.*/)?lib/([^/]+)/[^/]+$#\2#' <<<"$entries" | sort -u | grep -v '^$' || true)"
  [[ -n "$abis" ]] || { echo "AATD-88: $pkg ships no native libraries" >&2; status=1; continue; }
  for abi in $abis; do
    grep -qE "(^|/)lib/$abi/libastrid_android\.so$" <<<"$entries" \
      || { echo "AATD-88: $pkg ships lib/$abi/ without libastrid_android.so — set ndk.abiFilters to the ABIs scripts/build-core.sh builds" >&2; status=1; }
  done
  [[ $status -ne 0 ]] || echo "native ABIs in $(basename "$pkg"): $(echo $abis)"
done
exit $status
