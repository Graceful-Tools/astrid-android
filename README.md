# Astrid for Android

Native Android app for [Astrid](https://astrid.cc) task management.

**Repository:** https://github.com/Graceful-Tools/astrid-android
**Shared core (pinned by revision):** https://github.com/Graceful-Tools/astrid-core
**Web app and API:** https://github.com/Graceful-Tools/astrid-web
**iOS and Mac apps (the design and behaviour this app follows):** https://github.com/Graceful-Tools/astrid-ios
**Windows app:** https://github.com/Graceful-Tools/astrid-windows

## How it is built

```
app/            Kotlin, Jetpack Compose   screens, navigation, Android platform adapters
corebridge/     Kotlin                    UniFFI-generated bindings + a small facade (CoreSession, CoreRules)
core/           Rust                      astrid-android: the UniFFI crate over astrid-core
astrid-core     Rust (own repo)           models, API client, SQLite cache, Outbox, sync, every shared rule
      |
      v  HTTPS /api/v1/*
astrid-web (astrid.cc)
```

The shell renders and dispatches; **it decides nothing**. Everything that must read the same on
web, iOS, Mac, Windows and Android lives once in astrid-core and is locked by fixtures generated
from astrid-web. This is the arrangement the Windows app proved, with the binding layer the Apple
apps use (UniFFI), whose Kotlin generator is first-party.

## Getting started

Needs JDK 21, the Android SDK (platform 35, build-tools 35, NDK 27.2), Rust with the Android
targets, and `cargo-ndk`. See [docs/TESTING.md](./docs/TESTING.md) for the exact setup.

```bash
npm run predeploy            # the standard gate: core, native build, lint, unit tests, debug APK
npm run install:debug        # onto a running emulator or device
```

## Documentation

| File | Purpose |
|---|---|
| [docs/ASTRID.md](./docs/ASTRID.md) | Architecture and the rules — read first |
| [docs/PLAN.md](./docs/PLAN.md) | The build plan, milestone by milestone, and the board tasks behind it |
| [docs/PARITY.md](./docs/PARITY.md) | What this app does against iOS, row by row |
| [docs/TESTING.md](./docs/TESTING.md) | Toolchain, test layers, emulator |
| [brands/README.md](./brands/README.md) | Building a white-label brand: package, name, scheme, hosts, icons |

## License

MIT — see [LICENSE](./LICENSE).
