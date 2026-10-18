#!/usr/bin/env bash
# Build the native core for the app and regenerate the Kotlin bindings.
#
#   scripts/build-core.sh                       # arm64-v8a + x86_64 .so files, host library, bindings
#   scripts/build-core.sh --host-only           # host library + bindings only (the JVM tests)
#   scripts/build-core.sh --core ../astrid-core # against a local core checkout, for this build only
#   scripts/build-core.sh --release             # optimised .so files (release APKs)
#
# Outputs (none committed except the bindings):
#   corebridge/src/main/jniLibs/<abi>/libastrid_android.so     what the APK ships
#   corebridge/src/test/resources/native/libastrid_android.*   what JNA loads in JVM unit tests
#   corebridge/src/main/java/uniffi/astrid_android/*.kt        generated — committed, never edited
set -euo pipefail
cd "$(dirname "$0")/.."
root="$PWD"
[[ -f "$HOME/dev-tools/android-env.sh" ]] && source "$HOME/dev-tools/android-env.sh"

host_only=0; profile=dev; local_core=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --host-only) host_only=1; shift ;;
    --release) profile=release; shift ;;
    --core) local_core="$(cd "$2" && pwd)"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

cargo_args=()
if [[ -n "$local_core" ]]; then
  cargo_args+=(--config "patch.\"https://github.com/Graceful-Tools/astrid-core.git\".astrid-core.path=\"$local_core/crates/astrid-core\"")
fi
out_dir=debug; [[ "$profile" == release ]] && out_dir=release

cd core
# The host library: what the bindgen reads, and what the JVM tests load.
cargo build ${cargo_args[@]+"${cargo_args[@]}"} -p astrid-android --profile "$profile"
case "$(uname -s)" in
  Darwin) host_lib="target/$out_dir/libastrid_android.dylib" ;;
  *) host_lib="target/$out_dir/libastrid_android.so" ;;
esac
mkdir -p "$root/corebridge/src/test/resources/native"
cp "$host_lib" "$root/corebridge/src/test/resources/native/"

# Generated from the library itself, so the bindings can only describe what was built.
rm -rf "$root/corebridge/src/main/java/uniffi"
cargo run ${cargo_args[@]+"${cargo_args[@]}"} --quiet -p uniffi-bindgen -- generate \
  --library "$host_lib" --language kotlin --no-format \
  --out-dir "$root/corebridge/src/main/java"

if [[ "$host_only" == 0 ]]; then
  : "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME (source ~/dev-tools/android-env.sh)}"
  # Debug APKs ship the stripped `jni` profile (core/Cargo.toml), release APKs the release one.
  ndk_profile=(--profile jni); [[ "$profile" == release ]] && ndk_profile=(--release)
  cargo ndk ${cargo_args[@]+"${cargo_args[@]}"} -t arm64-v8a -t x86_64 --platform 28 \
    -o "$root/corebridge/src/main/jniLibs" build -p astrid-android ${ndk_profile[@]+"${ndk_profile[@]}"}
fi
echo "core built ($profile$([[ $host_only == 1 ]] && echo ', host only'))"
