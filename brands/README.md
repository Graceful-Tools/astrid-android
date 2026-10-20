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
| `astridUrlScheme` | `astrid` | The custom scheme: deep links, the launcher shortcut, and the sign-in callback the core accepts |
| `astridHandoffClientId` | (the core's `android`) | The id the server's hand-off registry knows the app by |
| `astridSupportEmail` | `support@astrid.cc` | Feedback mail |
| `astridInboundTaskEmail` | `remindme@astrid.cc` | Email-to-task address |
| `astridAgentEmailDomain` | `astrid.cc` | The domain shown on agent identities |

**The server must agree.** The scheme is the server's `BRAND.appUrlScheme` (astrid-web
`brands/*.brand.json` → `NEXT_PUBLIC_BRAND_APP_URL_SCHEME`), and the hand-off client id must be registered
in its `DesktopClient` registry, or sign-in delivers to the wrong app. Verified app links need the
server to serve `/.well-known/assetlinks.json` for the brand's package and release key.

Not yet brand-driven: the in-app accent colour and other `ui/theme` colours (Kotlin, ported from iOS
`Theme.swift`).
