# Testing

## Toolchain (macOS, arm64)

- JDK 21 (Temurin), Android SDK with `platforms;android-35`, `build-tools;35.0.0`,
  `ndk;27.2.12479018`, `emulator`, `system-images;android-35;google_apis;arm64-v8a`.
- Rust (pinned by `core/rust-toolchain.toml`) with `aarch64-linux-android` and
  `x86_64-linux-android`, and `cargo install cargo-ndk`.
- On the dev Mac these live under `~/dev-tools` and `~/Library/Android/sdk`;
  `source ~/dev-tools/android-env.sh` sets `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_NDK_HOME`, `PATH`.
  `scripts/predeploy.sh` sources it when present.

## Layers

| Layer | Where | Runs in |
|---|---|---|
| Native crate | `core/astrid-android/src/lib.rs` `#[cfg(test)]` | `cargo test` |
| Bridge against the real core | `corebridge/src/test/` — loads the host build of the crate through JNA | `testDebugUnitTest` |
| View models over a fake core | `app/src/test/` | `testDebugUnitTest` |
| Compose screens | `app/src/test/` with Robolectric | `testDebugUnitTest` |
| Smoke tests on a device | `app/src/androidTest/` — offline core seeded with `SeedCache` | `predeploy --full` |

## Emulator

```bash
avdmanager create avd -n astrid35 -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_8
emulator -avd astrid35 -no-window -no-audio -no-snapshot &   # headless
adb wait-for-device && npm run predeploy:full
```
