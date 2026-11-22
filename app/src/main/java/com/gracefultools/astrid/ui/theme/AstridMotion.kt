package com.gracefultools.astrid.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import com.astrid.design.AstridTokens.Motion
import kotlin.math.PI

/**
 * The tokens' motion as Compose specs. A token spring is a duration and a damping, as SwiftUI's
 * `.spring(duration:)` takes it; Compose takes a stiffness, which for that duration is (2π / d)².
 */
object AstridMotion {
    fun <T> spring(
        durationMillis: Int = Motion.springDurationMillis,
        damping: Float = Motion.springDamping,
    ): SpringSpec<T> {
        val omega = 2 * PI / (durationMillis / 1000.0)
        return spring(dampingRatio = damping, stiffness = (omega * omega).toFloat())
    }

    /** [spring], or no animation at all under reduced motion (DS17): the change lands at once. */
    fun <T> springOrSnap(
        reduced: Boolean,
        durationMillis: Int = Motion.springDurationMillis,
        damping: Float = Motion.springDamping,
    ): AnimationSpec<T> = if (reduced) snap() else spring(durationMillis, damping)
}
