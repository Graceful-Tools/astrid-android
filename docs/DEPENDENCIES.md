# Dependencies: locks, checksums and the Socket gate (ADEV-352)

Every Maven version the build resolves is **locked**, and every artifact it downloads has a
**pinned checksum**. Gradle enforces both on every build, locally and in CI:

| File | What it pins |
|---|---|
| `gradle/libs.versions.toml` | The versions we choose. Edit this one. |
| `<project>/gradle.lockfile` | Every version each project resolves, including the dependencies of our dependencies. Generated. |
| `buildscript-gradle.lockfile` | The plugins' own classpath (AGP, Kotlin, baseline profile). Generated. |
| `gradle/verification-metadata.xml` | The sha256 of every artifact and POM. Generated. |

So a version cannot change without a commit that shows it. And because the lockfiles name every
version, astrid-web's Socket gate (`socket-package-gate.yml`, which runs on every pull request)
checks the whole tree, not only what we declare.

## Adding or upgrading a dependency

1. Change the version in `gradle/libs.versions.toml`, or add the dependency to a project.
2. Run `scripts/write-dependency-locks.sh`. It rewrites the lockfiles and the checksums from
   scratch, so versions no longer used drop out. It needs the network, but not the NDK or native
   libraries.
3. Read the diff of the lockfiles: it lists every version that came in with yours.
4. Before installing, check the new versions with Socket:
   `node ../astrid-web/scripts/deps-gate.mjs check`. The pull request runs the same check.
5. Commit the version change, the lockfiles and `verification-metadata.xml` together.

A build that resolves a version that isn't in the lockfile fails with "Resolved … which is not part
of the dependency lock state". A download whose checksum isn't recorded fails with "Dependency
verification failed". Both mean step 2 was skipped. Never edit either file by hand to silence them.

**aapt2 is the exception that makes this a script.** AGP downloads one aapt2 jar per OS, and Gradle
records the checksum only for the jar the generating machine used. The script also fetches the
Linux, macOS and Windows jars from Google Maven and records theirs, so CI (Linux) and the dev Mac
both verify. `scripts/test-dependency-locks.sh` fails if any of the three is missing.

**Dependabot** is not configured in this repo. If it is ever added, its Gradle pull requests must
run `scripts/write-dependency-locks.sh` before merging, because the checksum file would otherwise
refuse them.

## Floors on what the tooling brings

The first Socket run over the lockfiles (2026-11-27) refused
`org.bouncycastle:bcprov-jdk18on:1.80.2` for a critical CVE. AGP 9.5.1 brings it into the plugins'
classpath and lint's (`androidLintTool`). It is build tooling, never in the APK. `build.gradle.kts`
moves every Bouncy Castle artifact up to `astrid.bouncyCastle` in `gradle.properties` (1.85), and
`scripts/test-dependency-locks.sh` fails if a lockfile pins any other version. Remove the rule once
an AGP upgrade brings that version or later on its own.

## What is not covered

- **Robolectric's `android-all` jars** are downloaded by Robolectric at test time, outside Gradle,
  so they are neither locked nor checksummed.
- **The Rust core** is locked by `core/Cargo.lock`, which the Socket gate reads as well, and is
  checked against RustSec by `cargo deny`.
- `org.rustls:rustls-platform-verifier` comes from the rustls project's own Maven archive, not
  Maven Central (`settings.gradle.kts`). Its version follows the `rustls-platform-verifier-android`
  crate in `core/Cargo.lock`, which is where Socket checks it.
