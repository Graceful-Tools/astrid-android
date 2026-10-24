package com.gracefultools.astrid.ui.lists

import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import com.gracefultools.astrid.model.InsightDuration
import com.gracefultools.astrid.model.InsightLane
import com.gracefultools.astrid.model.ListInsights
import com.gracefultools.astrid.ui.common.CoreViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.ZoneId
import kotlin.math.roundToLong

data class ListInsightsState(
    val days: Int = ListInsightsFormat.DEFAULT_WINDOW,
    val insights: ListInsights? = null,
    /** Which window failed, so choosing another is a fresh attempt (iOS `failedDays`). */
    val failedDays: Int? = null,
) {
    /** The answer for the chosen window; null while it loads. */
    val shown: ListInsights? get() = insights?.takeIf { it.window.days == days }
    val failed: Boolean get() = failedDays == days
}

/**
 * The Insights tab of List Settings (AATD-77) — iOS's ListInsightsView, web's ListInsightsPanel.
 * The numbers are the server's, asked through the core's `listInsights` in the device's zone so
 * days bucket like the reader's calendar; nothing is counted or cached here.
 */
class ListInsightsViewModel(
    private val core: Core,
    private val listId: String,
    private val timeZone: String = ZoneId.systemDefault().id,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CoreViewModel(dispatcher) {
    private val _state = MutableStateFlow(ListInsightsState())
    val state: StateFlow<ListInsightsState> = _state.asStateFlow()
    private var loading: Job? = null

    init { load(_state.value.days) }

    fun select(days: Int) {
        if (days == _state.value.days && !_state.value.failed) return
        _state.update { it.copy(days = days) }
        load(days)
    }

    private fun load(days: Int) {
        loading?.cancel()
        loading = work {
            try {
                val answer = core.run(command("listInsights", "listId" to listId, "days" to days, "timeZone" to timeZone))
                val insights = CoreJson.decodeFromJsonElement(ListInsights.serializer(), answer)
                _state.update { it.copy(insights = insights, failedDays = it.failedDays.takeIf { failed -> failed != days }) }
            } catch (_: CoreFailure) {
                _state.update { it.copy(failedDays = days) }
            } catch (_: IllegalArgumentException) {
                _state.update { it.copy(failedDays = days) }
            }
        }
    }
}

enum class InsightUnit { MINUTES, HOURS, DAYS }

/** How the Insights tab presents the numbers — iOS ListInsightsFormat, web ListInsightsPanel. */
object ListInsightsFormat {
    /** The windows the route offers, in picker order. */
    val WINDOWS = listOf(7, 30, 90)
    const val DEFAULT_WINDOW = 30

    private const val MINUTE = 60_000.0
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /**
     * A median at the one unit that reads naturally — minutes under an hour, hours under two days,
     * then days, to a tenth — or null for "—" (web `formatInsightDuration`).
     */
    fun duration(value: InsightDuration): Pair<InsightUnit, String>? {
        val ms = value.medianMs ?: return null
        return when {
            ms < HOUR -> InsightUnit.MINUTES to number(maxOf(1.0, Math.round(ms / MINUTE).toDouble()))
            ms < 2 * DAY -> InsightUnit.HOURS to number((ms / HOUR * 10).roundToLong() / 10.0)
            else -> InsightUnit.DAYS to number((ms / DAY * 10).roundToLong() / 10.0)
        }
    }

    /** Lane moves are recorded only from web's AWTD-1329 on, so an all-empty board says so. */
    fun lanesHaveHistory(lanes: List<InsightLane>): Boolean = lanes.any { it.sampleSize > 0 }

    /** `12`, `1.5` — a whole number without a trailing `.0`, as JavaScript prints one. */
    private fun number(value: Double): String =
        if (value == Math.rint(value)) value.toLong().toString() else value.toString()
}
