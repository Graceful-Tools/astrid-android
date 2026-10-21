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
class Preferences(context: Context) : PromptLedger, com.gracefultools.astrid.platform.ReviewLedger,
    com.gracefultools.astrid.platform.UpdateLedger {
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

    /** iOS's board full screen, kept as iOS keeps it (AATD-61). */
    var boardFullScreen: Boolean
        get() = prefs.getBoolean(BOARD_FULL_SCREEN, false)
        set(value) { prefs.edit().putBoolean(BOARD_FULL_SCREEN, value).apply() }

    /** Whether the pinned messages pane is folded to its rail (AATD-61). */
    var messagesPaneHidden: Boolean
        get() = prefs.getBoolean(MESSAGES_HIDDEN, false)
        set(value) { prefs.edit().putBoolean(MESSAGES_HIDDEN, value).apply() }

    // iOS ReviewPromptManager's UserDefaults (AATD-63).
    override var reviewPromptCount: Int
        get() = prefs.getInt(REVIEW_COUNT, 0)
        set(value) { prefs.edit().putInt(REVIEW_COUNT, value).apply() }

    override var lastReviewPromptMillis: Long?
        get() = prefs.getLong(REVIEW_LAST, -1).takeIf { it >= 0 }
        set(value) { prefs.edit().apply { if (value == null) remove(REVIEW_LAST) else putLong(REVIEW_LAST, value) }.apply() }

    override var hasReviewed: Boolean
        get() = prefs.getBoolean(REVIEWED, false)
        set(value) { prefs.edit().putBoolean(REVIEWED, value).apply() }

    // iOS AppUpdateService's "appUpdateDismissedVersion" (AATD-67): a Play version code here.
    override var dismissedUpdateVersion: Int?
        get() = prefs.getInt(UPDATE_DISMISSED, -1).takeIf { it >= 0 }
        set(value) { prefs.edit().apply { if (value == null) remove(UPDATE_DISMISSED) else putInt(UPDATE_DISMISSED, value) }.apply() }

    private companion object {
        const val BOARD_FULL_SCREEN = "boardFullScreen"
        const val MESSAGES_HIDDEN = "messagesPaneHidden"
        const val REVIEW_COUNT = "reviewPromptCount"
        const val REVIEW_LAST = "lastReviewPromptDate"
        const val REVIEWED = "hasReviewedApp"
        const val UPDATE_DISMISSED = "appUpdateDismissedVersion"
        const val THEME = "themeMode"
        const val PROMPT_COUNT = "notificationPromptCount"
        const val LAST_PROMPT = "lastNotificationPromptDate"
        const val ASKED_SYSTEM = "notificationPermissionAsked"
    }
}
