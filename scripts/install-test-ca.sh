#!/usr/bin/env bash
# Install AATD-100's throwaway test root as a USER CA on a rootable emulator (a google_apis
# "userdebug" image, not a Play Store one), so PlatformTlsTest can check that a user-installed root is
# honoured. `--remove` takes it out again; without it on the device, the test checks the refusal.
#
#   scripts/install-test-ca.sh [--remove]
#
# The root's key is not committed anywhere. Only `ca.pem` and the localhost server's PKCS12 are, and
# a root on a test emulator is trusted by that emulator alone.
set -euo pipefail
cd "$(dirname "$0")/.."
pem=app/src/androidTest/assets/aatd100/ca.pem
hash="$(openssl x509 -subject_hash_old -noout -in "$pem")"
target="/data/misc/user/0/cacerts-added/$hash.0"
adb root >/dev/null
adb wait-for-device
if [[ "${1:-}" == "--remove" ]]; then
  adb shell rm -f "$target"
  echo "removed $target"
  exit 0
fi
adb shell mkdir -p /data/misc/user/0/cacerts-added
adb push "$pem" "$target" >/dev/null
adb shell "chown system:system $target && chmod 644 $target && restorecon $target" || true
echo "installed $target"
