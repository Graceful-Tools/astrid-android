package com.gracefultools.astrid

/**
 * iOS `Brand.swift`: the one place a brand-bearing value lives. Each one is a BuildConfig field set
 * from a Gradle property (app/build.gradle.kts), and each defaults to hosted Astrid's, so a build
 * nobody configured behaves exactly as it always has.
 *
 * Gradle owns those defaults, and never passes a blank one. So this file names no Astrid value of its
 * own: a fallback here would be dead code that still ships "Astrid" and astrid.cc in every partner's
 * APK (AATD-90, `scripts/check-brand-literals.py --apk`).
 *
 * Build-time on purpose. The server's `/api/v1/capabilities` `brand` block carries text only, and it
 * leaves out the host and agent email domain deliberately: they are trust boundaries, and a server
 * must not be able to claim them (astrid-web capabilities route; iOS `ServerCapabilities.BrandInfo`).
 */
object Brand {
    /**
     * The server: what the core talks to (`CoreSession.start`'s `baseUrl`) and where every web link
     * points. iOS `Constants.API.baseURL` / `Brand.productionBaseURL`. No trailing slash.
     */
    val baseUrl: String = origin(BuildConfig.ASTRID_BASE_URL)

    /**
     * iOS `Brand.appName`, for code that is not drawing a resource: the same value as `@string/app_name`
     * (both from `astridAppName`), which is what views use (AATD-90).
     */
    val appName: String = BuildConfig.ASTRID_APP_NAME.trim()

    /** iOS `Brand.supportEmail`: where feedback mail goes. */
    val supportEmail: String = BuildConfig.ASTRID_SUPPORT_EMAIL.trim()

    /** iOS `Brand.inboundTaskEmail`: the address users email to create a task (AATD-73). */
    val inboundTaskEmail: String = BuildConfig.ASTRID_INBOUND_TASK_EMAIL.trim()

    /**
     * iOS `Brand.agentEmailDomain`: the domain of agent identities (`claude@astrid.cc`), separate from
     * the host because the server lets them differ. Only for display; an agent's real address is
     * whatever the server returned (AATD-73).
     */
    val agentEmailDomain: String = BuildConfig.ASTRID_AGENT_EMAIL_DOMAIN.trim().trimStart('@')

    /** Hosted Astrid's URL scheme — what the core assumes when it is told none (AWTD2-95). */
    const val ASTRID_URL_SCHEME = "astrid"

    /**
     * The app's own URL scheme (AATD-72): `astrid://tasks/<id>` and the sign-in callback. The
     * manifest registers the same one (`${astridUrlScheme}`), from the same Gradle property.
     */
    val urlScheme: String = BuildConfig.ASTRID_URL_SCHEME.trim().lowercase().ifEmpty { ASTRID_URL_SCHEME }

    /** Where the browser hand-off delivers: `astrid://auth/callback` (astrid-web `desktop-handoff.ts`). */
    val callbackPrefix: String = "$urlScheme://auth/callback"

    /**
     * The https hosts whose links open in the app — the server's own, and any others the brand
     * claims (astrid.cc and www.astrid.cc for Astrid). The manifest's app links are the same list.
     */
    val linkHosts: Set<String> = linkHosts(BuildConfig.ASTRID_LINK_HOSTS, baseUrl)

    /**
     * The id the server's hand-off registry knows this app by, or null for the core's own
     * (`android`). Partner servers that register their app under another id name it here.
     */
    val handoffClientId: String? = BuildConfig.ASTRID_HANDOFF_CLIENT_ID.trim().ifEmpty { null }

    /** [urlScheme] for the core's config, or null when it is Astrid's — so Astrid's config is unchanged. */
    val coreUrlScheme: String? = urlScheme.takeIf { it != ASTRID_URL_SCHEME }

    /**
     * Astrid blue: astrid-core `design/tokens.json` `brand.accent` (blue-600 since DS26, AWTD-1598 —
     * dark enough for a white label on it).
     */
    const val DEFAULT_ACCENT_HEX = "#2563eb"

    /** iOS `Brand.accentColorHex` (AATD-75): the brand accent, `#rrggbb` or `#rrggbbaa`. The theme's `Accent`. */
    val accentColorHex: String = resolveAccentHex(BuildConfig.ASTRID_ACCENT_COLOR)

    /** iOS `Brand.accentHoverColorHex`: the accent's pressed state (tokens `brand.accent-hover`, blue-700 for Astrid). */
    val accentHoverColorHex: String = resolveAccentHex(BuildConfig.ASTRID_ACCENT_HOVER_COLOR, fallback = DEFAULT_ACCENT_HOVER_HEX)

    /** Tokens `brand.accent-hover`. */
    const val DEFAULT_ACCENT_HOVER_HEX = "#1d4ed8"

    /** iOS `Brand.accentTextColorHex`: drawn on the accent, so a pale accent can take dark text. */
    val accentTextColorHex: String = resolveAccentHex(BuildConfig.ASTRID_ACCENT_TEXT_COLOR, fallback = "#ffffff")

    /**
     * The accent as text on a light card and on a dark one (tokens `brand.accent-ink` /
     * `accent-ink-dark`, AATD-90), or null when the brand leaves it to be derived from the accent.
     */
    val accentInkHex: String? = optionalHex(BuildConfig.ASTRID_ACCENT_INK_COLOR)
    val accentInkDarkHex: String? = optionalHex(BuildConfig.ASTRID_ACCENT_INK_DARK_COLOR)

    /**
     * The theme a fresh install starts on (AATD-90), as web's `NEXT_PUBLIC_BRAND_DEFAULT_THEME` is
     * the one a visitor starts on: Ocean for Astrid. A theme the user picks still wins. The launch
     * window's colour (`@color/launch_bg`) comes from the same Gradle property.
     */
    val defaultTheme: com.gracefultools.astrid.ui.theme.ThemeMode =
        com.gracefultools.astrid.ui.theme.ThemeMode.of(BuildConfig.ASTRID_DEFAULT_THEME)

    /** A configured `#rrggbb` as lower case, or null when it is blank or malformed. */
    fun optionalHex(raw: String?): String? =
        resolveAccentHex(raw, fallback = "").takeIf { it.length == 7 }

    /**
     * iOS `Brand.resolveAccentHex`: a configured colour as lower-case `#rrggbb` / `#rrggbbaa`, or
     * [fallback]. Total on purpose — a brand colour is chrome, so a typo renders Astrid's.
     */
    fun resolveAccentHex(raw: String?, fallback: String = DEFAULT_ACCENT_HEX): String {
        val digits = raw?.trim()?.removePrefix("#") ?: return fallback
        if (digits.length != 6 && digits.length != 8) return fallback
        if (!digits.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return fallback
        return "#" + digits.lowercase()
    }

    /** [base]'s host plus the comma-separated [configured] hosts, lower-cased. */
    fun linkHosts(configured: String, base: String): Set<String> =
        (listOfNotNull(runCatching { java.net.URI(base.trim()).host }.getOrNull()) + configured.split(','))
            .map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    /** A page on the server: `webUrl("/settings/account")` → `https://astrid.cc/settings/account`. */
    fun webUrl(path: String, base: String = baseUrl): String = base + "/" + path.trimStart('/')

    /** A configured origin, trimmed and without a trailing slash. */
    fun origin(raw: String): String = raw.trim().trimEnd('/')
}
