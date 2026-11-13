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

### From a deployment's profile (AATD-90)

A partner's server is configured by a profile in astrid-web `brands/*.brand.json`. The app's brand
is generated from the same file rather than typed again:

```bash
scripts/make-brand-variant.sh acme                       # ../astrid-web/brands/acme.brand.json → brands/acme
scripts/make-brand-variant.sh path/to/partner.brand.json # any profile, no astrid-web checkout
./gradlew assembleRelease -PastridBrand=acme
```

It maps the profile's `env` (name, wordmark, domain, URL scheme, emails, accent) and takes the
package from the first entry of `BRAND_ANDROID_APP_LINKS`. An optional `android` block holds what only
the app knows: `applicationId`, `appName`, `handoffClientId`, `linkHosts`, `linkAutoVerify`,
`accentHoverColor`, `accentTextColor`, `accentInk`, `accentInkDark`, `defaultTheme`, `versionCode`, `versionName`, `play` and the artwork (both
below). It writes `brand.properties`, the Play listing, the artwork and a copy of the profile. Re-run it after the profile changes;
predeploy fails while `brands/acme` is stale.

**Play listing.** `brands/<slug>/play/` has the same layout as Astrid's `app/src/main/play/` (title,
short and full description, first release notes). The text comes from `android.play`
(`title` ≤ 30, `shortDescription` ≤ 80, `fullDescription` ≤ 4000, `releaseNotes` ≤ 500 characters;
the script refuses longer text). With no `play` block, it writes a neutral draft in the brand's
name and domain, for the partner to replace.

**Artwork.** These are image paths relative to the profile. `sips` (macOS) or ImageMagick resizes them into
`brands/<slug>/res/`, as iOS does for its icon sets:

| Key | Becomes |
|---|---|
| `appIcon` | A 1024×1024 PNG: `mipmap-*/ic_launcher` (48 dp) and the adaptive icon's foreground, scaled to the 72 dp a launcher mask shows, on `iconBackground` |
| `iconBackground` | `#rrggbb` behind the adaptive icon (`ic_launcher_background`). Default: the profile's accent |
| `monochromeIcon` | A 1024×1024 silhouette (only its alpha counts): the themed icon (Android 13+) and `ic_notification`. Without it, the brand's adaptive icon has no themed layer, so it never shows Astrid's silhouette. The notification icon stays Astrid's, and the script says so |
| `characterImage` | A square PNG that replaces `astrid_character` (sign-in, sidebar, empty states, agents), at 512 px |

Re-running the script removes only the files it wrote last time (listed in `res/.make-brand-variant`), so
removing a key from the profile brings back Astrid's art. It never touches resources a partner added by hand.
Predeploy merges a brand with generated art. The checkbox PNGs and Ocean's cyan are still Astrid's; a brand that
wants neither cyan nor Astrid's look starts on another theme (`defaultTheme`).

**Literal audit.** `scripts/check-brand-literals.py` lists every string in a brand build's merged
resources and manifest that contains one of the profile's `expect.forbidLiterals`. Predeploy runs it
on acme, including its Play listing (`--text brands/acme/play`), against a budget that only goes
down (24: the webhook hint's `ASTRID_WEBHOOK_SECRET` and `npx astrid-agent`, in 12 locales). It also
reads the packaged APK's dex (`--apk`), where a fallback in Kotlin would hide from a resource audit. The
dex is mostly names (`AstridTheme`, `astrid.sqlite`), so there a name counts only as a whole word in its
own case, and a domain in any case: acme's dex has none. That is why `Brand.kt` names no Astrid value of
its own: Gradle gives every key Astrid's value when it is unset. The core library's strings are still to
come in AATD-90. Anything else a partner supplies in `res/` by hand, as below.

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
| `astridAccentColor` | `#2563eb` | The in-app accent (`Brand.accentColorHex` → the theme's `Accent`): buttons, links, focus, Material's primary |
| `astridAccentHoverColor` | `#1d4ed8` | The accent's pressed state |
| `astridAccentTextColor` | `#ffffff` | Text and icons drawn on the accent — change it with a pale accent |
| `astridAccentInkColor` | (Astrid's token, or derived from the accent) | The accent as text on a light card (tokens `brand.accent-ink`): links, selected rows. Must read at 4.5:1 on white and on the accent's 12% wash; `make-brand-variant.sh` refuses less, and the app falls back to the derived ink |
| `astridAccentInkDarkColor` | (Astrid's token, or derived from the accent) | The same on a dark card (gray-800, `accent-ink-dark`): Dark's focus line, selection outline and low priority |
| `astridDefaultTheme` | `ocean` | The theme a fresh install starts on: `ocean`, `light`, `dark` or `auto`. A theme the user picks still wins. The launch window and Android 12+ splash take that theme's page colour (`@color/launch_bg`: cyan, white, gray-900; white for `auto`). From `android.defaultTheme`, else the profile's `NEXT_PUBLIC_BRAND_DEFAULT_THEME`; the script refuses another name, the app reads one as Ocean |
| `astridVersionCode` | `1` | The package's versionCode, 1–2100000000. Play needs a higher one for every upload, so CI usually passes `-PastridVersionCode` (or `ORG_GRADLE_PROJECT_astridVersionCode`) |
| `astridVersionName` | `0.1.0` | The version people see |

Astrid's palette comes from astrid-core `design/tokens.json` (accent `#2563EB`, hover `#1D4ED8`, DS26;
AWTD-1598). Android's copy is still by hand until the colours are generated from the tokens (AATD-85,
through core's Compose emitter AWTD2-163). The accent's text shades — `brand.accent-ink` and
`accent-ink-dark` — are Astrid's blues for Astrid; for a partner accent they are worked out from it, as
astrid-web does, so text in the accent reads at 4.5:1 on a card and on its own wash. See *From a deployment's profile* above
(AATD-90).

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

The rest of the `ui/theme` palette (backgrounds, text, borders) is not brand-driven on iOS either. Its
source is astrid-core `design/tokens.json`; Android's hand copy (taken from iOS `Theme.swift`) is replaced
by a generated one in AATD-85.
