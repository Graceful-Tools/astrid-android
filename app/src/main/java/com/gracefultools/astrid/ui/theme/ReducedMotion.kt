package com.gracefultools.astrid.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Reduced motion (register DS17), iOS's `ReducedMotion.swift` on Android. Android has no separate
 * Reduce Motion switch: Accessibility → "Remove animations" sets the animator duration scale to 0,
 * and that is the signal. With it on, nothing springs, bounces, pops or slides; short fades keep
 * playing, as on iOS and web. Read it through [LocalReducedMotion], which [AstridTheme] provides.
 */
object ReducedMotion {
    /** The system's setting, read at the moment of the call. */
    fun isEnabled(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

/** Whether the system asks for reduced motion; follows the setting while the app is open. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** The setting as Compose state, updated when it changes. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember(context) { mutableStateOf(ReducedMotion.isEnabled(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = ReducedMotion.isEnabled(context) }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}
