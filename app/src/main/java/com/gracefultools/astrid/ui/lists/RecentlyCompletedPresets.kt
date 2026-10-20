package com.gracefultools.astrid.ui.lists

import androidx.annotation.StringRes
import com.gracefultools.astrid.R
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The Admin tab's "Recently completed" choices, in iOS's order (iOS RecentlyCompletedPresets.swift,
 * itself web's `lib/recently-completed-presets.ts`). [window] is what `updateList` stores as
 * `recentlyCompletedWindow` (astrid-core `RecentlyCompletedWindow`, tagged by `kind`); null is the
 * legacy 24-hour default. Applying the window is the core's (`rowsForList`); this is only the
 * picker's table, and moves to the core once it answers the presets itself.
 */
enum class RecentlyCompletedPreset(@StringRes val label: Int, val window: JsonObject?) {
    DEFAULT_24H(R.string.lists_recent_default_24h, null),
    LAST_7_DAYS(R.string.lists_recent_last_7_days, duration(7)),
    LAST_14_DAYS(R.string.lists_recent_last_14_days, duration(14)),
    LAST_28_DAYS(R.string.lists_recent_last_28_days, duration(28)),
    LAST_30_DAYS(R.string.lists_recent_last_30_days, duration(30)),
    SINCE_LAST_SUNDAY(R.string.lists_recent_since_sunday, buildJsonObject { put("kind", "since-weekday"); put("weekday", 0) }),
    SINCE_LAST_MONDAY(R.string.lists_recent_since_monday, buildJsonObject { put("kind", "since-weekday"); put("weekday", 1) }),
    SINCE_FIRST_OF_MONTH(R.string.lists_recent_since_first, buildJsonObject { put("kind", "since-day-of-month"); put("day", 1) }),
    SINCE_SPECIFIC_DATE(R.string.lists_recent_since_date, null),
    ;

    companion object {
        /** iOS findPresetForWindow: none is the default, any since-date is the date preset. */
        fun forWindow(window: JsonObject?): RecentlyCompletedPreset {
            if (window == null) return DEFAULT_24H
            if ((window["kind"] as? JsonPrimitive)?.content == "since-date") return SINCE_SPECIFIC_DATE
            return entries.firstOrNull { it.window != null && same(it.window, window) } ?: DEFAULT_24H
        }

        private fun same(a: JsonObject, b: JsonObject): Boolean =
            a.keys == b.keys && a.keys.all { (a[it] as? JsonPrimitive)?.content == (b[it] as? JsonPrimitive)?.content }
    }
}

private fun duration(days: Int) = buildJsonObject { put("kind", "duration"); put("amount", days); put("unit", "day") }
