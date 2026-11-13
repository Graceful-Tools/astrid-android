package com.gracefultools.astrid.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How many lines a task's title may take in a row (register DS19; Jon, 2026-11-10). It wraps
 * without limit by default, as on iOS, and the Appearance setting "Task titles" chooses wrap, two
 * lines or one. The choice belongs to the device, like the theme: [wire] is the value web stores
 * (`lib/design/row-title.ts`), so the three clients say the same thing.
 */
enum class RowTitleLines(val wire: String, val maxLines: Int) {
    WRAP("wrap", Int.MAX_VALUE), TWO("2", 2), ONE("1", 1);

    companion object {
        /** A stored value, or [WRAP] for anything this build does not know. */
        fun of(wire: String?): RowTitleLines = entries.firstOrNull { it.wire == wire?.trim() } ?: WRAP
    }
}

/** The device's "Task titles" choice, provided at the root and read by every task row. */
val LocalRowTitleLines = staticCompositionLocalOf { RowTitleLines.WRAP }
