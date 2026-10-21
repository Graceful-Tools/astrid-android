#!/usr/bin/env bash
# The standard gate. CI runs the same steps (.github/workflows/ci.yml), so green here means green there.
#
#   scripts/predeploy.sh           core (fmt, clippy, test) + native build + gradle lint, unit tests, debug APK
#   scripts/predeploy.sh --quick   skips lint and the Android cross-build of the core
#   scripts/predeploy.sh --full    adds the instrumented smoke tests on a running emulator/device
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -f "$HOME/dev-tools/android-env.sh" ]] && source "$HOME/dev-tools/android-env.sh"

mode="${1:-}"
step() { printf '\n── %s ──\n' "$*"; }

if [[ -f core/Cargo.toml ]]; then
  step "core: fmt, clippy, test"
  (cd core && cargo fmt --all -- --check && cargo clippy --all-targets -- -D warnings && cargo test --workspace)
  step "core: native libraries and Kotlin bindings"
  if [[ "$mode" == "--quick" ]]; then scripts/build-core.sh --host-only; else scripts/build-core.sh; fi
  step "bindings are what the core generates"
  git diff --exit-code -- corebridge/src/main/java/uniffi \
    || { echo "generated bindings changed: commit them with the core change that caused it" >&2; exit 1; }
fi

# The scheduled loop runs under launchd on the dev Mac only (docs/AUTOMATION.md), and its test
# drives the real zsh script against stubs, so CI (ubuntu) skips it.
if [[ "$(uname)" == Darwin ]]; then
  step "scripts: the scheduled /fixall loop"
  scripts/test-fixall-loop.sh
fi

step "gradle: unit tests"
./gradlew --console=plain testDebugUnitTest
if [[ "$mode" != "--quick" ]]; then
  step "gradle: lint and debug APK"
  ./gradlew --console=plain lintDebug assembleDebug
  # A white-label brand still merges: its manifest placeholders, resources and generated shortcut
  # (AATD-72, brands/README.md). Stops short of packaging, so the APK above stays the Astrid one.
  step "gradle: the example brand's manifest and resources"
  ./gradlew --console=plain -PastridBrand=example :app:processDebugResources
fi
if [[ "$mode" == "--full" ]]; then
  step "gradle: instrumented smoke tests (needs a device or emulator)"
  adb get-state >/dev/null 2>&1 || { echo "no device: start the emulator first (docs/TESTING.md)" >&2; exit 1; }
  ./gradlew --console=plain connectedDebugAndroidTest
fi
printf '\npredeploy %s: green\n' "${mode:-standard}"
