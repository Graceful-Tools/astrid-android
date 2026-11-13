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
  # CI's supply-chain job, run here too: without it main sat red on RUSTSEC-2026-0433 for five
  # merges that all passed this gate (AWTD-1621). Advisories change daily, so a red here can be new.
  step "core: cargo-deny (advisories, bans, licenses, sources)"
  if command -v cargo-deny >/dev/null; then
    (cd core && cargo deny check)
  elif [[ -n "${CI:-}" ]]; then
    echo "cargo-deny is not installed (CI runs it in its own supply-chain job)"
  else
    echo "cargo-deny is not installed: cargo install --locked cargo-deny" >&2; exit 1
  fi
  step "core: native libraries and Kotlin bindings"
  if [[ "$mode" == "--quick" ]]; then scripts/build-core.sh --host-only; else scripts/build-core.sh; fi
  step "bindings are what the core generates"
  git diff --exit-code -- corebridge/src/main/java/uniffi \
    || { echo "generated bindings changed: commit them with the core change that caused it" >&2; exit 1; }
  if [[ "$mode" != "--quick" ]]; then
    # What a release APK ships (the debug one ships the unoptimised `jni` profile), measured as the
    # app's packaging strips it. arm64 only: x86_64 is emulators, and the two move together.
    step "core: the release arm64 library is within its size budget (AATD-91)"
    (cd core && cargo ndk -t arm64-v8a --platform 28 build -p astrid-android --release)
    scripts/check-native-size.sh core/target/aarch64-linux-android/release/libastrid_android.so arm64-v8a
  fi
fi

# The scheduled loop runs under launchd on the dev Mac only (docs/AUTOMATION.md), and its test
# drives the real zsh script against stubs, so CI (ubuntu) skips it.
step "scripts: a brand from its profile, the literal audit (AATD-90), the size budget (AATD-91)"
scripts/test-make-brand-variant.sh
scripts/test-check-native-size.sh
# brands/acme is generated from astrid-web's acme profile: stale when that profile has moved on.
web="${ASTRID_WEB_DIR:-../astrid-web}"
if [[ -f "$web/brands/acme.brand.json" ]]; then
  scripts/make-brand-variant.sh "$web/brands/acme.brand.json" >/dev/null
  git diff --exit-code -- brands/acme \
    || { echo "brands/acme is stale: commit what scripts/make-brand-variant.sh acme wrote" >&2; exit 1; }
fi

# Hand-written colours, sizes and copy in ui/ (AATD-86): a count may fall, never rise. Lower the
# budget with --update when yours falls; raising one is an edit to the JSON with its reason.
step "design: no hand-written colour, size or copy count rose (AATD-86)"
scripts/test-report-design-metrics.sh
python3 -I scripts/report-design-metrics.py --check

if [[ "$(uname)" == Darwin ]]; then
  step "scripts: the scheduled /fixall loop"
  scripts/test-fixall-loop.sh
fi

step "gradle: unit tests"
./gradlew --console=plain testDebugUnitTest
if [[ "$mode" != "--quick" ]]; then
  step "gradle: lint and debug APK"
  ./gradlew --console=plain lintDebug assembleDebug
  step "native libraries: every shipped ABI carries the core (AATD-88)"
  scripts/check-native-abis.sh app/build/outputs/apk/debug/app-debug.apk
  # A white-label brand still merges: its manifest placeholders, resources and generated shortcut
  # (AATD-72, brands/README.md). Stops short of packaging; only the acme audit below packages.
  step "gradle: the example brand's manifest and resources"
  ./gradlew --console=plain -PastridBrand=example :app:processDebugResources
  # Its links verify (astridLinkAutoVerify=true, AATD-76); Astrid's are checked in BrandIdentityTest.
  grep -q 'android:autoVerify="true"' app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml \
    || { echo "the example brand's https links are not autoVerify (AATD-76)" >&2; exit 1; }
  # A brand's generated artwork merges over the app's (AATD-90): icons from one 1024-px source, a
  # silhouette and the character. Needs sips or ImageMagick to make it; skipped where neither exists.
  if command -v sips >/dev/null || command -v magick >/dev/null || command -v convert >/dev/null; then
    step "gradle: a brand's generated icons and character art merge"
    art="$(mktemp -d)"; trap 'rm -rf "$art" brands/.artcheck' EXIT
    python3 -I -c 'import json,sys; p=json.load(open(sys.argv[1])); p["android"]={"appIcon":"icon.png","monochromeIcon":"mono.png","characterImage":"character.png","iconBackground":"#123456"}; json.dump(p,open(sys.argv[2],"w"))' \
      brands/acme/profile.brand.json "$art/artcheck.brand.json"
    scale() { if command -v sips >/dev/null; then sips -z 1024 1024 "$1" --out "$2" >/dev/null; else "$(command -v magick || command -v convert)" "$1" -resize 1024x1024! "$2"; fi; }
    scale app/src/main/res/drawable-nodpi/astrid_character.png "$art/icon.png"
    scale app/src/main/res/mipmap-xxxhdpi/ic_launcher_monochrome.png "$art/mono.png"
    cp app/src/main/res/drawable-nodpi/check_box_1.png "$art/character.png"
    scripts/make-brand-variant.sh "$art/artcheck.brand.json" --out brands/.artcheck 2>/dev/null
    ./gradlew --console=plain -PastridBrand=.artcheck :app:processDebugResources
    grep -q '<color name="ic_launcher_background">#FF123456</color>' \
      app/build/intermediates/incremental/debug/mergeDebugResources/merged.dir/values/values.xml \
      || { echo "the brand's generated icon background did not replace Astrid's (AATD-90)" >&2; exit 1; }
  fi
  # A partner's build should name the partner, not Astrid. Not there yet: the budget is how many
  # strings still do, and it only goes down (check-brand-literals.py; the goal is 0, AATD-90).
  # Packaged, so the dex is audited too: Kotlin's own strings and any fallback left in it. And the
  # native core: its default server, https://astrid.cc, is 2 of the 26 until AWTD-1627.
  step "gradle: the acme brand names no Astrid, in resources, dex or core (budget 26)"
  # The version is per build too (AATD-90): what -P passes is what the package says.
  ./gradlew --console=plain -PastridBrand=acme -PastridVersionCode=42 -PastridVersionName=4.2.0 \
    :app:assembleDebug
  # Moved aside, so app-debug.apk is never a partner's build mistaken for Astrid's.
  acme_apk=app/build/outputs/apk/debug/acme-debug.apk
  mv app/build/outputs/apk/debug/app-debug.apk "$acme_apk"
  acme_manifest=app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
  grep -q 'android:versionCode="42"' "$acme_manifest" && grep -q 'android:versionName="4.2.0"' "$acme_manifest" \
    || { echo "-PastridVersionCode/-PastridVersionName did not reach the manifest (AATD-90)" >&2; exit 1; }
  # Its Play listing is text a person reads as well.
  scripts/check-brand-literals.py brands/acme/profile.brand.json \
    app/build/intermediates/incremental/debug/mergeDebugResources/merged.dir "$acme_manifest" \
    --text brands/acme/play --apk "$acme_apk" --budget 26
fi
if [[ "$mode" == "--full" ]]; then
  step "gradle: instrumented smoke tests (needs a device or emulator)"
  adb get-state >/dev/null 2>&1 || { echo "no device: start the emulator first (docs/TESTING.md)" >&2; exit 1; }
  ./gradlew --console=plain connectedDebugAndroidTest
fi
printf '\npredeploy %s: green\n' "${mode:-standard}"
