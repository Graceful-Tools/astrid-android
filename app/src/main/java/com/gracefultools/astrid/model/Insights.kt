package com.gracefultools.astrid.model

import kotlinx.serialization.Serializable

/**
 * `listInsights`: what a list has been doing over a window, computed once on the server (web
 * AWTD-1329, AWTD-1333; iOS AITD-545). These only mirror the answer — the source of truth is
 * astrid-web `lib/list-insights.ts`.
 */
@Serializable
data class ListInsights(
    val window: InsightWindow = InsightWindow(),
    /** One per calendar day, oldest first. */
    val series: List<InsightDay> = emptyList(),
    val totals: InsightTotals = InsightTotals(),
    val createToComplete: InsightDuration = InsightDuration(),
    /** Null for a list that is not a board. */
    val lanes: List<InsightLane>? = null,
    val contributors: List<InsightContributor> = emptyList(),
)

@Serializable
data class InsightWindow(val days: Int = 30, val timeZone: String = "UTC", val start: String = "", val end: String = "")

@Serializable
data class InsightDay(val date: String, val created: Int = 0, val completed: Int = 0)

@Serializable
data class InsightTotals(val created: Int = 0, val completed: Int = 0)

/** A median, or null when there was nothing to take one of — shown as "—", never 0. */
@Serializable
data class InsightDuration(val medianMs: Double? = null, val sampleSize: Int = 0)

/** The median stay in one board column, in column order. */
@Serializable
data class InsightLane(val role: String, val name: String, val medianMs: Double? = null, val sampleSize: Int = 0) {
    val duration: InsightDuration get() = InsightDuration(medianMs, sampleSize)
}

/** A top contributor, with the profile's metrics for this list (AWTD-1333) — absent ones are 0. */
@Serializable
data class InsightContributor(
    val userId: String,
    val name: String? = null,
    val image: String? = null,
    val isAIAgent: Boolean = false,
    val completed: Int = 0,
    val inspired: Int = 0,
    val supported: Int = 0,
    val created: Int = 0,
    val edited: Int = 0,
    val commented: Int = 0,
    val total: Int = 0,
)
