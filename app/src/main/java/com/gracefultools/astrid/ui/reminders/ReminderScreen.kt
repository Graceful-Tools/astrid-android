package com.gracefultools.astrid.ui.reminders

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.West
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.model.TaskDetail
import com.gracefultools.astrid.ui.common.AstridSpeechBubble
import com.gracefultools.astrid.ui.common.Avatar
import com.gracefultools.astrid.ui.common.CheckboxArt
import com.gracefultools.astrid.ui.common.initialsOf
import com.gracefultools.astrid.ui.tasks.dueText
import com.gracefultools.astrid.ui.theme.Accent
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Radius
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.Success
import com.gracefultools.astrid.ui.theme.TextStyles
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** iOS's orange (Color.orange): the "counting on you" strip and the Tomorrow swipe. */
private val Orange = Color(0xFFFF9500)

/**
 * What a tapped reminder opens (iOS ReminderPresentationModifier): the reminder card for one task,
 * the triage deck for several. Full screen over the app, as iOS's fullScreenCover (AATD-54).
 */
@Composable
fun ReminderOverlay(vm: ReminderViewModel, onOpenTask: (String) -> Unit, onFinished: (Set<String>) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished(vm.handled.toSet()) }
    if (state.loading || state.finished) return
    Dialog(
        onDismissRequest = vm::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // iOS draws both with Theme's light (or dark) tokens, not Ocean's: a white card over the scrim.
        val palette = if (AstridTheme.colors.isDark) com.gracefultools.astrid.ui.theme.DarkColors else com.gracefultools.astrid.ui.theme.LightColors
        androidx.compose.runtime.CompositionLocalProvider(com.gracefultools.astrid.ui.theme.LocalAstridColors provides palette) {
            if (state.isTriage) {
                TriageDeck(state, vm, onOpenTask = { vm.close(); onOpenTask(it) })
            } else {
                state.current?.let { detail -> ReminderCard(detail, state, vm, onOpenTask = { vm.close(); onOpenTask(it) }) }
            }
        }
    }
}

/** iOS ReminderConstants.getReminderPhrase: a greeting (a due one once due) and an encouragement. */
@Composable
private fun rememberPhrase(isDue: Boolean): String {
    val resources = LocalContext.current.resources
    val appName = stringResource(R.string.app_name)
    return remember(isDue) {
        val greetings = resources.getStringArray(if (isDue) R.array.reminder_phrases_due else R.array.reminder_phrases)
        val responses = resources.getStringArray(R.array.reminder_responses)
        "${String.format(greetings.random(), appName)} ${responses.random()}"
    }
}

/** iOS ReminderView: the task, Astrid's nag, who is counting on you, Complete and the snoozes. */
@Composable
private fun ReminderCard(detail: TaskDetail, state: ReminderState, vm: ReminderViewModel, onOpenTask: (String) -> Unit) {
    val colors = AstridTheme.colors
    val phrase = rememberPhrase(vm.isDue(detail))
    val id = detail.task.id
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            // A tap outside the card closes it, as iOS's dimmed overlay; the close button is the labelled way.
            .pointerInput(Unit) { detectTapGestures { vm.close() } }
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.s16).widthIn(max = 500.dp).fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(colors.bgPrimary)
                .clickable(onClickLabel = stringResource(R.string.reminders_triage_card_hint)) { onOpenTask(id) }
                .padding(bottom = Spacing.s24),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = vm::close, modifier = Modifier.padding(Spacing.s8)) {
                    Icon(Icons.Filled.Cancel, stringResource(R.string.reminders_triage_close), tint = colors.textMuted, modifier = Modifier.size(28.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s24)) {
                // The task as its row draws it; the checkbox completes it.
                Row(
                    Modifier.padding(horizontal = Spacing.s16).fillMaxWidth().clip(RoundedCornerShape(Radius.large)).background(colors.card)
                        .padding(Spacing.s12),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
                ) {
                    val completeLabel = stringResource(R.string.reminders_complete)
                    CheckboxArt(
                        detail.task.priority, repeating = detail.task.repeating != null && detail.task.repeating != "never", checked = false,
                        modifier = Modifier.clickable(onClickLabel = completeLabel, role = Role.Checkbox) { vm.complete(id) }
                            .semantics { contentDescription = completeLabel },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(detail.task.title, style = TextStyles.rowTitle, color = colors.textPrimary)
                        dueText(detail.due)?.let { Text(it, style = TextStyles.subheadline, color = if (detail.isOverdue) com.gracefultools.astrid.ui.theme.Error else colors.textSecondary) }
                    }
                }
                AstridSpeechBubble(phrase, Modifier.padding(horizontal = Spacing.s16), characterSize = 80.dp)
                if (state.countingOnYou.isNotEmpty()) {
                    Row(
                        Modifier.padding(horizontal = Spacing.s16).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Orange.copy(alpha = 0.1f)).border(1.dp, Orange.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(Spacing.s12),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                            state.countingOnYou.forEach { person ->
                                Avatar(person.image, initialsOf(person.display), 32.dp, border = Color.White)
                            }
                        }
                        Text(stringResource(R.string.reminders_counting_on_you), style = TextStyles.subheadline, fontWeight = FontWeight.Medium, color = Orange)
                    }
                }
                Column(Modifier.padding(horizontal = Spacing.s16), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                    ActionButton(stringResource(R.string.reminders_complete), Success, Color.White, Icons.Filled.CheckCircle) { vm.complete(id) }
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                        SnoozeButton(stringResource(R.string.reminders_snooze_15_minutes), Modifier.fillMaxWidth()) { vm.snooze(id, 15) }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                            SnoozeButton(stringResource(R.string.reminders_snooze_1_day), Modifier.weight(1f)) { vm.snooze(id, 1440) }
                            SnoozeButton(stringResource(R.string.reminders_snooze_1_week), Modifier.weight(1f)) { vm.snooze(id, 10080) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(title: String, background: Color, foreground: Color, icon: ImageVector?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(background).clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.s16),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = foreground, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(Spacing.s8)) }
        Text(title, style = TextStyles.headline, color = foreground)
    }
}

@Composable
private fun SnoozeButton(title: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(colors.bgSecondary).border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = Spacing.s16),
        contentAlignment = Alignment.Center,
    ) { Text(title, style = TextStyles.headline, color = colors.textPrimary) }
}

/**
 * iOS ReminderTriageView: one task per card, a swipe per decision — left completes, right is
 * tomorrow, down keeps — and a labelled button for each beneath the card, for TalkBack and for
 * discovering the swipes.
 */
@Composable
private fun TriageDeck(state: ReminderState, vm: ReminderViewModel, onOpenTask: (String) -> Unit) {
    val colors = AstridTheme.colors
    Box(Modifier.fillMaxSize().background(colors.bgPrimary).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 500.dp).fillMaxSize().padding(Spacing.s16),
            verticalArrangement = Arrangement.spacedBy(Spacing.s16),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reminders_triage_title), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (state.current != null) {
                    Text(stringResource(R.string.reminders_triage_progress, state.index + 1, state.deck.size), style = TextStyles.caption, color = colors.textMuted)
                }
                IconButton(onClick = vm::close) {
                    Icon(Icons.Filled.Cancel, stringResource(R.string.reminders_triage_close), tint = colors.textMuted, modifier = Modifier.size(28.dp))
                }
            }
            val task = state.current
            if (task == null) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Verified, null, tint = Success, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.size(Spacing.s16))
                    Text(stringResource(R.string.reminders_triage_caught_up), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Spacer(Modifier.size(Spacing.s16))
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(Accent).clickable(role = Role.Button, onClick = vm::close)
                            .padding(horizontal = Spacing.s32, vertical = Spacing.s12),
                    ) { Text(stringResource(R.string.reminders_triage_done), style = TextStyles.headline, color = Color.White) }
                }
            } else {
                var pending by remember(task.task.id) { mutableStateOf<TriageAction?>(null) }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SwipeCard(task, onPending = { pending = it }, onDecide = vm::decide, onOpen = { onOpenTask(task.task.id) }, pending = pending)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    TriageButton(TriageAction.COMPLETE, pending, Modifier.weight(1f)) { vm.decide(TriageAction.COMPLETE) }
                    TriageButton(TriageAction.POSTPONE, pending, Modifier.weight(1f)) { vm.decide(TriageAction.POSTPONE) }
                }
                TriageButton(TriageAction.KEEP, pending, Modifier.fillMaxWidth()) { vm.decide(TriageAction.KEEP) }
            }
        }
    }
}

@Composable
private fun SwipeCard(task: TaskDetail, pending: TriageAction?, onPending: (TriageAction?) -> Unit, onDecide: (TriageAction) -> Unit, onOpen: () -> Unit) {
    val colors = AstridTheme.colors
    val scope = rememberCoroutineScope()
    val drag = remember(task.task.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val density = LocalDensity.current
    fun inDp(offset: Offset) = with(density) { Offset(offset.x.toDp().value, offset.y.toDp().value) }
    val openLabel = stringResource(R.string.reminders_triage_card_hint)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 220.dp)
            .offset { IntOffset(drag.value.x.roundToInt(), drag.value.y.roundToInt()) }
            .rotate(inDp(drag.value).x / 20f)
            .shadow(8.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(colors.bgSecondary)
            .border(if (pending == null) 1.dp else 3.dp, pending?.let { triageColor(it, colors.textMuted) } ?: colors.border, RoundedCornerShape(16.dp))
            .pointerInput(task.task.id) {
                detectDragGestures(
                    onDragEnd = {
                        val dp = inDp(drag.value)
                        val action = ReminderViewModel.swipe(dp.x, dp.y)
                        onPending(null)
                        if (action != null) onDecide(action) else scope.launch { drag.animateTo(Offset.Zero) }
                    },
                    onDragCancel = { onPending(null); scope.launch { drag.animateTo(Offset.Zero) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { drag.snapTo(drag.value + amount) }
                    val dp = inDp(drag.value + amount)
                    onPending(ReminderViewModel.swipe(dp.x, dp.y))
                }
            }
            .clickable(onClickLabel = openLabel, onClick = onOpen)
            .padding(Spacing.s24),
        verticalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        task.listChips.firstOrNull()?.let { Text(it.name, style = TextStyles.caption, color = colors.textMuted) }
        Text(task.task.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            val dueColor = if (task.isOverdue) com.gracefultools.astrid.ui.theme.Error else colors.textMuted
            Icon(Icons.Filled.CalendarToday, null, tint = dueColor, modifier = Modifier.size(16.dp))
            Text(dueText(task.due) ?: stringResource(R.string.picker_no_due_date), style = TextStyles.body, color = dueColor)
        }
        if (task.task.description.isNotBlank()) {
            Text(task.task.description, style = TextStyles.body, color = colors.textMuted, maxLines = 4)
        }
    }
}

private fun triageColor(action: TriageAction, muted: Color): Color = when (action) {
    TriageAction.COMPLETE -> Success
    TriageAction.POSTPONE -> Orange
    TriageAction.KEEP -> muted
}

@Composable
private fun TriageButton(action: TriageAction, pending: TriageAction?, modifier: Modifier, onClick: () -> Unit) {
    val colors = AstridTheme.colors
    val color = triageColor(action, colors.textMuted)
    val highlighted = pending == action
    val (title, icon) = when (action) {
        TriageAction.COMPLETE -> stringResource(R.string.reminders_complete) to Icons.Filled.West
        TriageAction.POSTPONE -> stringResource(R.string.time_tomorrow) to Icons.Filled.East
        TriageAction.KEEP -> stringResource(R.string.reminders_triage_keep) to Icons.Filled.South
    }
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (highlighted) color else colors.bgSecondary)
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = Spacing.s12),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (highlighted) Color.White else color
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(Spacing.s8))
        Text(title, style = TextStyles.headline, color = fg)
    }
}
