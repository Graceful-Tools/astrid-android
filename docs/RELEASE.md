# Releasing

*Nothing here reaches anyone until a person uploads to Google Play. Ask Jon first (CLAUDE.md).*

## Build

```bash
scripts/build-core.sh --release        # optimised .so files for both ABIs
ASTRID_UPLOAD_KEYSTORE=… ASTRID_UPLOAD_KEYSTORE_PASSWORD=… \
ASTRID_UPLOAD_KEY_ALIAS=… ASTRID_UPLOAD_KEY_PASSWORD=… \
  ./gradlew bundleRelease               # app/build/outputs/bundle/release/app-release.aab
```

Without the four variables a release build is signed with the debug key: installable for testing
(`assembleRelease`, then `adb install`), refused by Play. The upload key never enters the repo
(`.gitignore` excludes `*.jks`, `*.keystore`, `keystore.properties`).

Measured 2026-10-18 on the API 35 emulator: R8-minified release starts cold in ~0.7 s (debug ~2.9 s);
the universal APK is 37 MB (two ABIs), and Play's per-ABI splits roughly halve what a phone downloads.

## Before the first upload (needs Jon)

1. **Decide the applicationId** — `com.gracefultools.astrid` today; permanent once uploaded.
2. **Create the Play app** in the Graceful Tools developer account and enrol in Play App Signing.
3. **Make an upload key** and store it as the four CI secrets above.
4. **App Links:** astrid.cc and www.astrid.cc serve `/.well-known/assetlinks.json` with the Play
   app-signing SHA-256 (astrid-web AWTD-1303; the file is in brands/README.md), then Astrid's
   `astridLinkAutoVerify` default becomes `true` in `app/build.gradle.kts` (AATD-76).
   Until then those links offer a chooser rather than opening the app directly.
5. **Listing:** short description, full description, screenshots (the smoke test writes phone
   screenshots to `/data/local/tmp/astrid-*.png`), feature graphic, privacy policy URL
   (astrid.cc/privacy), data-safety form (account email and name; tasks; no ads, no tracking).
6. **versionCode** in `app/build.gradle.kts` goes up by one per upload.
