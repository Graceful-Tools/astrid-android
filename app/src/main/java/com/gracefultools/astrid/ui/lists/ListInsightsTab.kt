package com.gracefultools.astrid.ui.lists

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.InsightContributor
import com.gracefultools.astrid.model.InsightDay
import com.gracefultools.astrid.model.InsightDuration
import com.gracefultools.astrid.model.InsightLane
import com.gracefultools.astrid.model.ListInsights
import com.gracefultools.astrid.ui.common.Avatar
import com.gracefultools.astrid.ui.common.initialsOf
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.Success
import com.gracefultools.astrid.ui.theme.TextStyles
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.max

/**
 * iOS ListInsightsView (AITD-545): the 7 / 30 / 90-day picker, New and Completed per day with their
 * totals, the median time to complete, the median time in each column on a board, and the top
 * contributors. It only draws what [ListInsightsViewModel] was answered.
 */
@Composable
internal fun ListInsightsTab(vm: ListInsightsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.s16).navigationBarsPadding().testTag("listInsights"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SegmentedTabs(
            ListInsightsFormat.WINDOWS.map { null to windowLabel(it) },
            selected = ListInsightsFormat.WINDOWS.indexOf(state.days).coerceAtLeast(0),
            onSelect = { vm.select(ListInsightsFormat.WINDOWS[it]) },
        )
        val insights = state.shown
        when {
            state.failed -> Note(stringResource(R.string.list_insights_load_failed))
            insights != null -> {
                Activity(insights)
                DurationRow(stringResource(R.string.list_insights_time_to_complete), insights.createToComplete)
                insights.lanes?.let { Lanes(it) }
                Contributors(insights.contributors)
            }
            else -> Note(stringResource(R.string.list_insights_loading))
        }
    }
}

@Composable
private fun windowLabel(days: Int): String = when (days) {
    7 -> stringResource(R.string.list_insights_days7)
    90 -> stringResource(R.string.list_insights_days90)
    else -> stringResource(R.string.list_insights_days30)
}

@Composable
private fun Activity(insights: ListInsights) {
    val colors = AstridTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Heading(stringResource(R.string.list_insights_activity))
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.list_insights_created_total, insights.totals.created.toString()) + " · " +
                    stringResource(R.string.list_insights_completed_total, insights.totals.completed.toString()),
                style = TextStyles.caption, color = colors.textSecondary,
            )
        }
        LineChart(insights.series, Modifier.fillMaxWidth().height(176.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s16)) {
            Legend(colors.accent, stringResource(R.string.list_insights_created))
            Legend(Success, stringResource(R.string.list_insights_completed))
        }
    }
}

/** Swift Charts' two LineMarks: a line per series over the days, a few gridlines, dated ticks. */
@Composable
private fun LineChart(series: List<InsightDay>, modifier: Modifier) {
    val colors = AstridTheme.colors
    val created = colors.accent
    val grid = colors.border
    val top = niceTop(series.maxOfOrNull { max(it.created, it.completed) } ?: 0)
    val ticks = yTicks(top)
    val dates = dateTicks(series)
    Column(modifier) {
        Row(Modifier.weight(1f)) {
            Canvas(Modifier.weight(1f).fillMaxSize().padding(vertical = 6.dp)) {
                ticks.forEach { value ->
                    val y = size.height * (1f - value.toFloat() / top)
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                fun line(pick: (InsightDay) -> Int, color: Color) {
                    if (series.isEmpty()) return
                    val step = if (series.size > 1) size.width / (series.size - 1) else 0f
                    val path = Path()
                    series.forEachIndexed { i, day ->
                        val point = Offset(i * step, size.height * (1f - pick(day).toFloat() / top))
                        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
                }
                line({ it.created }, created)
                line({ it.completed }, Success)
            }
            Column(Modifier.padding(start = 6.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                ticks.reversed().forEach { Text(it.toString(), style = TextStyles.caption2, color = colors.textSecondary) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            dates.forEachIndexed { i, label ->
                if (i > 0) Spacer(Modifier.weight(1f))
                Text(label, style = TextStyles.caption2, color = colors.textSecondary)
            }
        }
    }
}

/** A y-axis top that divides into four whole steps, and is never below 4. */
private fun niceTop(max: Int): Int {
    if (max <= 4) return 4
    val step = ceil(max / 4.0).toInt()
    return step * 4
}

private fun yTicks(top: Int): List<Int> = (0..4).map { top * it / 4 }

/** About five dated ticks, "MMM d" in the reader's locale, from the series' own days. */
private fun dateTicks(series: List<InsightDay>): List<String> {
    if (series.isEmpty()) return emptyList()
    val count = minOf(5, series.size)
    val format = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "MMMd"))
    return (0 until count).map { i ->
        val index = if (count == 1) 0 else i * (series.size - 1) / (count - 1)
        runCatching { LocalDate.parse(series[index].date).format(format) }.getOrDefault(series[index].date)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(label, style = TextStyles.caption, color = AstridTheme.colors.textSecondary)
    }
}

@Composable
private fun Lanes(lanes: List<InsightLane>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Heading(stringResource(R.string.list_insights_time_in_lane))
        if (ListInsightsFormat.lanesHaveHistory(lanes)) {
            lanes.forEach { DurationRow(it.name, it.duration) }
        } else {
            Note(stringResource(R.string.list_insights_not_enough_history) + " " + stringResource(R.string.list_insights_lane_history_note))
        }
    }
}

/** One median: "—" when there is none, otherwise the duration and how many it came from. */
@Composable
private fun DurationRow(label: String, value: InsightDuration) {
    val colors = AstridTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = TextStyles.subheadline, color = colors.textSecondary, modifier = Modifier.weight(1f))
        val median = ListInsightsFormat.duration(value)
        if (median != null) {
            val (unit, number) = median
            Text(
                when (unit) {
                    InsightUnit.MINUTES -> stringResource(R.string.list_insights_duration_minutes, number)
                    InsightUnit.HOURS -> stringResource(R.string.list_insights_duration_hours, number)
                    InsightUnit.DAYS -> stringResource(R.string.list_insights_duration_days, number)
                },
                style = TextStyles.subheadline.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary,
            )
            Text(stringResource(R.string.list_insights_sample_size, value.sampleSize.toString()), style = TextStyles.caption, color = colors.textMuted)
        } else {
            Text("—", style = TextStyles.subheadline, color = colors.textMuted)
        }
    }
}

@Composable
private fun Contributors(people: List<InsightContributor>) {
    val colors = AstridTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading(stringResource(R.string.list_insights_contributors))
        if (people.isEmpty()) Note(stringResource(R.string.list_insights_no_activity))
        people.forEach { person ->
            Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Avatar(person.image, initialsOf(person.name), 24.dp, shape = CircleShape)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(person.name ?: "—", style = TextStyles.subheadline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (person.isAIAgent) Icon(
                            Icons.Outlined.Memory, stringResource(R.string.lists_ai_coding_agent),
                            tint = colors.textSecondary, modifier = Modifier.size(12.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.list_insights_profile_counts, person.completed.toString(), person.inspired.toString(), person.supported.toString()),
                        style = TextStyles.caption, color = colors.textSecondary,
                    )
                    Text(
                        stringResource(R.string.list_insights_activity_counts, person.created.toString(), person.edited.toString(), person.commented.toString()),
                        style = TextStyles.caption, color = colors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) = Text(text, style = TextStyles.subheadline.copy(fontWeight = FontWeight.SemiBold), color = AstridTheme.colors.textPrimary)

@Composable
private fun Note(text: String) = Text(text, style = TextStyles.caption, color = AstridTheme.colors.textSecondary)
