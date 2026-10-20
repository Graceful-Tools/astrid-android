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

    /** A page on the server: `webUrl("/settings/account")` → `https://astrid.cc/settings/account`. */
    fun webUrl(path: String, base: String = baseUrl): String = base + "/" + path.trimStart('/')

    /** A configured origin, trimmed and without a trailing slash; [fallback] when unset or blank. */
    fun origin(raw: String?, fallback: String): String = raw?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: fallback
}
