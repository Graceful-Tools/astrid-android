# Testing

## Toolchain (macOS, arm64)

- JDK 21 (Temurin), Android SDK with `platforms;android-37.0`, `build-tools;36.0.0`,
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

## Live: signed in as the test account

```bash
scripts/live-test.sh        # emulator running; astrid-web checked out beside this repo
```

`LiveAccountTest` runs the real core against astrid.cc as **uitest@astrid.cc** — the iOS UI suite's
dedicated account, minted by astrid-web `scripts/uitest-account.ts --cookie`. Minting also **resets
that account's fixtures** (every task on it is deleted and `lib/uitest-fixtures.ts` recreated), so
mint once per run and reuse the cookie. The credential lives in an in-memory store for the run only:
the device's own sign-in is never read or replaced. Without the `astridLiveCookie` argument every
live test skips, so `predeploy --full` stays offline.

Traps: an emulator started with a stale `-dns-server` reaches nothing (`adb shell ping astrid.cc`);
a process's first request can fail while it is still starting, which is why the harness retries
fetching the user before it launches the activity.

## Startup: baseline profile and cold start

The release build ships a baseline profile (`app/src/release/generated/baselineProfiles/`), installed
by `profileinstaller` where Play has not. Regenerate it after a change to the startup path, with the
emulator (or a phone) connected and nothing covering the app:

```bash
./gradlew :app:generateBaselineProfile
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest   # cold start: no compilation vs the profile
```

The benchmark's numbers land in `baselineprofile/build/outputs/connected_android_test_additional_output/`.
An emulator's are for comparing runs on that emulator, not for quoting as a phone's.
