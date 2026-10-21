# Brands

An Android build is hosted Astrid unless it is told otherwise (AATD-71, AATD-72, AATD-73). A brand
is a directory here, selected with `-PastridBrand=<name>`:

```
brands/<name>/brand.properties   the values below
brands/<name>/res/               resources that replace the app's by name: mipmap-*/ic_launcher*,
                                 drawable/ic_launcher_foreground.xml, drawable-*/ic_notification.png,
                                 values/colors.xml (ic_launcher_background), …
```

```bash
source ~/dev-tools/android-env.sh
./gradlew assembleRelease -PastridBrand=example      # brands/example
./gradlew assembleDebug -PastridBrand=example -PastridAppName="Example Beta"   # -P beats the file
```

Any key can also be passed alone as `-P<key>=<value>` without a brand directory. A blank value is
unset. With nothing set, every value is Astrid's and the build is the one it always was.

| Key | Astrid's | What it sets |
|---|---|---|
| `astridApplicationId` | `com.gracefultools.astrid` | The package (Play listing, install identity). The Kotlin namespace stays. |
| `astridAppName` | `Astrid` | `@string/app_name`: the launcher label and the app's name in copy |
| `astridWordmark` | `astrid` | `@string/brand_wordmark`, the sidebar's wordmark |
| `astridBaseUrl` | `https://astrid.cc` | The server the core talks to and every web link (`Brand.baseUrl`) |
| `astridLinkHosts` | the server's host, plus `www.` for an apex domain | The https hosts whose links open the app (manifest + `DeepLinks`); at most two |
| `astridLinkAutoVerify` | `false` | `true` makes Android verify those hosts, so their links open the app with no chooser. Only once every host serves the `assetlinks.json` below |
| `astridUrlScheme` | `astrid` | The custom scheme: deep links, the launcher shortcut, and the sign-in callback the core accepts |
| `astridHandoffClientId` | (the core's `android`) | The id the server's hand-off registry knows the app by |
| `astridSupportEmail` | `support@astrid.cc` | Feedback mail |
| `astridInboundTaskEmail` | `remindme@astrid.cc` | Email-to-task address |
| `astridAgentEmailDomain` | `astrid.cc` | The domain shown on agent identities |
| `astridAccentColor` | `#3b82f6` | The in-app accent (`Brand.accentColorHex` → the theme's `Accent`): buttons, links, focus, Material's primary |
| `astridAccentHoverColor` | `#2563eb` | The accent's pressed state |
| `astridAccentTextColor` | `#ffffff` | Text and icons drawn on the accent — change it with a pale accent |

Colours are `#rrggbb` or `#rrggbbaa` (iOS `BrandAccentColor`, `BrandAccentHoverColor`,
`BrandAccentTextColor`; astrid-web `NEXT_PUBLIC_BRAND_ACCENT_*`). A malformed one is Astrid's, as on iOS.

**The server must agree.** The scheme is the server's `BRAND.appUrlScheme` (astrid-web
`brands/*.brand.json` → `NEXT_PUBLIC_BRAND_APP_URL_SCHEME`), and the hand-off client id must be registered
in its `DesktopClient` registry, or sign-in delivers to the wrong app.

### Verified app links (`astridLinkAutoVerify=true`)

Android checks, at install, that **every** link host serves
`https://<host>/.well-known/assetlinks.json` as `application/json`, with no redirect, naming the
package and the SHA-256 of the key the installed APK is signed with. One host failing fails them
all, and the links fall back to a chooser (Android 11 and older) or the browser (12 and newer):

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "<astridApplicationId>",
    "sha256_cert_fingerprints": ["<AA:BB:…, 32 colon-separated bytes>"]
  }
}]
```

The fingerprint is the **app-signing** key's: with Play App Signing that is Play Console → Test and
release → App integrity → App signing key certificate, not the upload key. A build signed with
another key (a debug or sideloaded APK) can be listed beside it. `keytool -list -v -keystore <ks>`
prints a keystore's. Check a device with `adb shell pm get-app-links <astridApplicationId>`
(`verified` per host); re-verify with `adb shell pm verify-app-links --re-verify <astridApplicationId>`.

Astrid's own site does not serve this file yet, so Astrid's build leaves the key `false`.

The rest of the `ui/theme` palette (backgrounds, text, borders) is iOS `Theme.swift`'s and is not
brand-driven on iOS either.
