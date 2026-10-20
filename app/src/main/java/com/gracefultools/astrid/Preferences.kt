package com.gracefultools.astrid

import android.content.Context
import com.gracefultools.astrid.platform.PromptLedger
import com.gracefultools.astrid.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-device choices that are not account data — the theme, as iOS keeps `themeMode` in
 * @AppStorage, and the notification prompt's counts, as iOS keeps them in UserDefaults. Account
 * settings live in the core.
 */
class Preferences(context: Context) : PromptLedger {
    private val prefs = context.getSharedPreferences("astrid", Context.MODE_PRIVATE)
    private val _theme = MutableStateFlow(ThemeMode.of(prefs.getString(THEME, null)))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(THEME, mode.wire).apply()
        _theme.value = mode
    }

    override var promptCount: Int
        get() = prefs.getInt(PROMPT_COUNT, 0)
        set(value) { prefs.edit().putInt(PROMPT_COUNT, value).apply() }

    override var lastPromptMillis: Long?
        get() = prefs.getLong(LAST_PROMPT, -1).takeIf { it >= 0 }
        set(value) { prefs.edit().apply { if (value == null) remove(LAST_PROMPT) else putLong(LAST_PROMPT, value) }.apply() }

    /** Whether the system's own permission dialog has been shown, which tells "refused" from "never asked". */
    var askedSystemPermission: Boolean
        get() = prefs.getBoolean(ASKED_SYSTEM, false)
        set(value) { prefs.edit().putBoolean(ASKED_SYSTEM, value).apply() }

    private companion object {
        const val THEME = "themeMode"
        const val PROMPT_COUNT = "notificationPromptCount"
        const val LAST_PROMPT = "lastNotificationPromptDate"
        const val ASKED_SYSTEM = "notificationPermissionAsked"
    }
}
