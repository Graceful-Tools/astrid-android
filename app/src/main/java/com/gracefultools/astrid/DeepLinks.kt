package com.gracefultools.astrid

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A file shared from another app, already copied into app storage where the core can read it. */
data class SharedFile(val path: String, val name: String, val mimeType: String?, val size: Long)

/** What the app was opened with, held until the signed-in screens are ready to take it. */
sealed interface Arrival {
    /** `astrid.cc/t/AATD-7`, `astrid.cc/tasks/<id>`, `astrid://tasks/<id>`: open that task. */
    data class OpenTask(val ref: String) : Arrival
    /** `astrid://lists/<id>`, `astrid.cc/lists/<id>`: show that list. */
    data class OpenList(val id: String) : Arrival
    /** `astrid://users/<id>`: a profile. */
    data class OpenProfile(val userId: String) : Arrival
    /** `astrid://settings/<page>`, `astrid.cc/settings/<page>`: [route] is the screen that page is. */
    data class OpenSettings(val route: String) : Arrival
    /** `astrid://s/<code>`, `astrid.cc/s/<code>`: a share link, resolved to a task or a list. */
    data class Shortcode(val code: String) : Arrival
    /** A page the app has no screen for: shown in a Custom Tab (iOS's in-app browser). */
    data class OpenWeb(val url: String) : Arrival
    /**
     * Share-to-Astrid (iOS's share extension): a file, or text (an Android extra), or nothing — the
     * "New task" launcher shortcut, also an Android extra.
     */
    data class NewTask(val text: String, val file: SharedFile? = null) : Arrival
    /** A reminder notification was tapped: one task opens the reminder screen, several the triage deck. */
    data class Reminder(val taskIds: List<String>) : Arrival
}

class DeepLinks {
    private val _pending = MutableStateFlow<Arrival?>(null)
    val pending: StateFlow<Arrival?> = _pending.asStateFlow()
    fun offer(arrival: Arrival) { _pending.value = arrival }
    fun take(): Arrival? = _pending.value.also { _pending.value = null }

    companion object {
        const val WEB_ORIGIN = "https://astrid.cc"
        private val WEB_HOSTS = setOf("astrid.cc", "www.astrid.cc")

        /**
         * The screen a settings page (iOS `SettingsPage`, the web's `/settings/<page>`) opens, or
         * null for a page iOS does not know. Pages without an Android screen yet open Settings.
         */
        fun settingsRoute(page: String): String? = when (page) {
            "profile" -> "profile"
            "agents" -> "agents"
            // `api-access` is the retired name for Connections, kept for old links (iOS).
            "connections", "api-access" -> "connections"
            "account" -> "account"
            "reminders" -> "settings/reminders"
            "appearance" -> "settings/appearance"
            "language" -> "settings/language"
            "chatgpt", "contacts", "debug", "about" -> "settings"
            else -> null
        }

        /**
         * A link the app knows, or null — iOS's DeepLinkManager. Ids are checked as iOS's
         * APIPathSafety does before they are routed on: a link is attacker-supplied input.
         */
        fun parse(link: String): Arrival? {
            if (link == "astrid://new") return Arrival.NewTask("")
            val uri = runCatching { java.net.URI(link) }.getOrNull() ?: return null
            return when {
                uri.scheme == "astrid" -> custom(uri)
                uri.scheme == "https" && uri.host in WEB_HOSTS -> web(link, uri)
                else -> null
            }
        }

        private fun segments(path: String?): List<String> = path.orEmpty().split('/').filter { it.isNotEmpty() }

        /** `astrid://<host>/<id>`. */
        private fun custom(uri: java.net.URI): Arrival? {
            val host = uri.host ?: return null
            val id = segments(uri.rawPath).firstOrNull()?.takeIf(::isValidId)
            return when (host) {
                // `task` is Android's first spelling; iOS and the web say `tasks`.
                "tasks", "task" -> id?.let(Arrival::OpenTask)
                "lists" -> id?.let(Arrival::OpenList)
                "users" -> id?.let(Arrival::OpenProfile)
                "settings" -> Arrival.OpenSettings(segments(uri.rawPath).firstOrNull()?.let(::settingsRoute) ?: "settings")
                "s" -> id?.let(Arrival::Shortcode)
                // The browser hand-off's callback is the sign-in screen's, never a page.
                "auth", "new" -> null
                else -> Arrival.OpenWeb("$WEB_ORIGIN/$host${uri.rawPath.orEmpty()}")
            }
        }

        /** `https://astrid.cc/<what>/<id>`; anything else on the site opens in a Custom Tab. */
        private fun web(link: String, uri: java.net.URI): Arrival? {
            val parts = segments(uri.rawPath)
            val first = parts.firstOrNull() ?: return null
            val id = parts.getOrNull(1)?.takeIf(::isValidId)
            return when (first) {
                "tasks", "t" -> id?.let(Arrival::OpenTask)
                "lists" -> id?.let(Arrival::OpenList)
                "settings" -> {
                    val page = parts.getOrNull(1) ?: return Arrival.OpenSettings("settings")
                    settingsRoute(page)?.let(Arrival::OpenSettings) ?: Arrival.OpenWeb(link)
                }
                "s" -> id?.let(Arrival::Shortcode)
                else -> Arrival.OpenWeb(link)
            }
        }

        /** iOS APIPathSafety.isValidIdentifier: 1–128 of letters, digits, `-` and `_`. */
        fun isValidId(id: String): Boolean = id.length in 1..128 && id.all { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_' }

        /**
         * Where a share link goes. The core's `resolveShortcode` answers `{targetType, targetId}`
         * (iOS asks `/api/v1/shortcodes/<code>`); when it cannot — offline, unknown, or a core that
         * does not have the command yet — the link opens on the web, as iOS falls back.
         */
        suspend fun resolveShortcode(core: com.gracefultools.astrid.core.Core, code: String): Arrival {
            val answer = runCatching {
                core.run(com.gracefultools.astrid.core.command("resolveShortcode", "code" to code)) as? kotlinx.serialization.json.JsonObject
            }.getOrNull()
            fun field(name: String) = (answer?.get(name) as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
            val id = field("targetId")?.takeIf(::isValidId)
            return when (field("targetType")) {
                "task" -> id?.let(Arrival::OpenTask)
                "list" -> id?.let(Arrival::OpenList)
                else -> null
            } ?: Arrival.OpenWeb("$WEB_ORIGIN/s/$code")
        }

        /** What a share carries, as one line for the title: subject, then text (often a URL). */
        fun shared(subject: String?, text: String?): String =
            listOfNotNull(subject?.trim()?.takeIf { it.isNotEmpty() }, text?.trim()?.takeIf { it.isNotEmpty() })
                .distinct().joinToString(" ")
    }
}
