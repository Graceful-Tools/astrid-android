package com.gracefultools.astrid

/**
 * iOS `Brand.swift`: the one place a brand-bearing value lives. Each one is a BuildConfig field set
 * from a Gradle property (app/build.gradle.kts), and each defaults to hosted Astrid's, so a build
 * nobody configured behaves exactly as it always has.
 *
 * Build-time on purpose. The server's `/api/v1/capabilities` `brand` block carries text only, and it
 * leaves out the host and agent email domain deliberately: they are trust boundaries, and a server
 * must not be able to claim them (astrid-web capabilities route; iOS `ServerCapabilities.BrandInfo`).
 */
object Brand {
    /** Hosted Astrid's origin: the default, and the only place the literal appears in the app. */
    const val ASTRID_ORIGIN = "https://astrid.cc"

    /**
     * The server: what the core talks to (`CoreSession.start`'s `baseUrl`) and where every web link
     * points. iOS `Constants.API.baseURL` / `Brand.productionBaseURL`. No trailing slash.
     */
    val baseUrl: String = origin(BuildConfig.ASTRID_BASE_URL, ASTRID_ORIGIN)

    /** iOS `Brand.supportEmail`: where feedback mail goes. */
    val supportEmail: String = BuildConfig.ASTRID_SUPPORT_EMAIL.trim().ifEmpty { "support@astrid.cc" }

    /** iOS `Brand.inboundTaskEmail`: the address users email to create a task (AATD-73). */
    val inboundTaskEmail: String = BuildConfig.ASTRID_INBOUND_TASK_EMAIL.trim().ifEmpty { "remindme@astrid.cc" }

    /**
     * iOS `Brand.agentEmailDomain`: the domain of agent identities (`claude@astrid.cc`), separate from
     * the host because the server lets them differ. Only for display; an agent's real address is
     * whatever the server returned (AATD-73).
     */
    val agentEmailDomain: String = BuildConfig.ASTRID_AGENT_EMAIL_DOMAIN.trim().trimStart('@').ifEmpty { "astrid.cc" }

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

    /** [base]'s host plus the comma-separated [configured] hosts, lower-cased. */
    fun linkHosts(configured: String, base: String): Set<String> =
        (listOfNotNull(runCatching { java.net.URI(base.trim()).host }.getOrNull()) + configured.split(','))
            .map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    /** A page on the server: `webUrl("/settings/account")` → `https://astrid.cc/settings/account`. */
    fun webUrl(path: String, base: String = baseUrl): String = base + "/" + path.trimStart('/')

    /** A configured origin, trimmed and without a trailing slash; [fallback] when unset or blank. */
    fun origin(raw: String?, fallback: String): String = raw?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: fallback
}
