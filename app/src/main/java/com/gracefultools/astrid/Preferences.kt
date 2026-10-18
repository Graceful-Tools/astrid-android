package com.gracefultools.astrid

import android.content.Context
import com.gracefultools.astrid.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-device choices that are not account data — the theme, as iOS keeps `themeMode` in
 * @AppStorage. Account settings live in the core.
 */
class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("astrid", Context.MODE_PRIVATE)
    private val _theme = MutableStateFlow(ThemeMode.of(prefs.getString(THEME, null)))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(THEME, mode.wire).apply()
        _theme.value = mode
    }

    private companion object { const val THEME = "themeMode" }
}
