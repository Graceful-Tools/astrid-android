package com.gracefultools.astrid.ui.theme

/**
 * Animation timings the core's `AstridTokens.Motion` has no entry for (ADEV-196, step 5). Each mirrors
 * the iOS screen it names. Reduced motion (DS17) still stops these through [LocalReducedMotion].
 */
object MotionTiming {
    /** One rise or fall of a typing dot: iOS AgentTypingIndicator's `easeInOut(duration: 0.4)`. */
    const val typingDotBounceMillis: Int = 400

    /** How far each typing dot starts behind the one before: iOS's `.delay(0.15)`. */
    const val typingDotStaggerMillis: Int = 150

    /** The spring that settles the board on a column after a fling. */
    const val boardFlingStiffness: Float = 400f
}

/** How long things stay on screen, or wait before they happen (ADEV-196). */
object ScreenTiming {
    /** An error banner dismisses itself after this. iOS's AppActions uses 5 s. */
    const val errorBannerMillis: Long = 6_000L

    /** "Copied" shows for this long before the button reverts, as on iOS. */
    const val copiedMillis: Long = 2_000L

    /** An agent hub flash message stays this long, as on iOS. */
    const val flashMillis: Long = 3_000L

    /** The new-task sheet waits this long for its slide-in before it focuses the title. */
    const val sheetFocusMillis: Long = 500L

    /** Search waits this long before it focuses the field. */
    const val searchFocusMillis: Long = 300L

    /** The pull-to-refresh indicator shows for at least this long. */
    const val refreshIndicatorMillis: Long = 600L

    /** One frame of the board's edge scroll while dragging. */
    const val frameMillis: Long = 16L

    /** A running timer's display updates once a second. */
    const val clockTickMillis: Long = 1_000L
}
