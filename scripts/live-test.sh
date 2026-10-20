#!/usr/bin/env bash
# Device tests against astrid.cc, signed in as the dedicated uitest@astrid.cc account (the iOS UI
# suite's). Mints a fresh session — which also resets that account's fixtures — through astrid-web,
# then runs only LiveAccountTest on the running emulator/device.
#
#   scripts/live-test.sh [--web ../astrid-web] [--tour [out-dir]]   (--tour screenshots every main screen)
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -f "$HOME/dev-tools/android-env.sh" ]] && source "$HOME/dev-tools/android-env.sh"
web="../astrid-web"
target="com.gracefultools.astrid.LiveAccountTest"
out=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --web) web="$2"; shift 2 ;;
    --tour) target="com.gracefultools.astrid.LiveAccountTest#tour"; out="${2:-build/live-shots}"; shift; [[ $# -gt 0 ]] && shift ;;
    *) shift ;;
  esac
done
adb get-state >/dev/null 2>&1 || { echo "no device: start the emulator first (docs/TESTING.md)" >&2; exit 1; }
# A Custom Tab left over from a sign-in check covers the app and every test times out.
adb shell am force-stop com.android.chrome || true
cookie="$(cd "$web" && npx tsx scripts/uitest-account.ts --cookie | tail -1)"
[[ "$cookie" == *session-token=* ]] || { echo "could not mint the test account's session" >&2; exit 1; }
./gradlew --console=plain connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="$target" \
  "-Pandroid.testInstrumentationRunnerArguments.astridLiveCookie=$cookie"
if [[ -n "$out" ]]; then
  mkdir -p "$out"
  for f in $(adb shell ls /data/local/tmp/ | tr -d '\r' | grep '^astrid-live-tour'); do adb pull -q "/data/local/tmp/$f" "$out/$f"; done
  echo "screenshots in $out"
fi
