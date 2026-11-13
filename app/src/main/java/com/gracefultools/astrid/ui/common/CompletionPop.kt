package com.gracefultools.astrid.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import com.astrid.design.AstridTokens
import com.gracefultools.astrid.ui.theme.AstridMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The checkbox's drawn scale, exposed to tests (AATD-85, DS16). */
val CheckboxScale = SemanticsPropertyKey<Float>("CheckboxScale")

/**
 * Completing a task (register DS16, Jon 2026-11-10): the Mac's motion and iOS's haptic together.
 * The checkbox pops to `state.complete-pop-scale` on `motion.spring` and settles back, and the
 * phone gives a confirm haptic. Reopening does neither. Compose scales every animation by the
 * system's animation setting, so "Remove animations" leaves the checkbox still.
 */
class CompletionPop internal constructor(
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
) {
    private val animatable = Animatable(1f)
    val scale: Float get() = animatable.value

    /** Call on every tap of the checkbox; [completes] is whether this tap completes the task. */
    fun tapped(completes: Boolean) {
        if (!completes) return
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch {
            animatable.animateTo(AstridTokens.State.completePopScale, AstridMotion.spring())
            animatable.animateTo(1f, AstridMotion.spring())
        }
    }
}

@Composable
fun rememberCompletionPop(): CompletionPop {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    return remember(scope, haptics) { CompletionPop(scope, haptics) }
}

/** Draws [pop]'s scale on the element it modifies. */
fun Modifier.completionPop(pop: CompletionPop): Modifier = this
    .graphicsLayer { scaleX = pop.scale; scaleY = pop.scale }
    .semantics { this[CheckboxScale] = pop.scale }
