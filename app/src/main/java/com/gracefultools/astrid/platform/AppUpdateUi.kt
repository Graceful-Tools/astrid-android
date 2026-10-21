package com.gracefultools.astrid.platform

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles
import androidx.compose.ui.text.TextStyle

/**
 * Google Play In-App Updates (flexible) as the [UpdateSource] (AATD-67) — iOS asks astrid-web what
 * the App Store has; on Android, Play answers for this install, as it does for the review flow.
 *
 * A download Play finishes is installed when the app next goes to the background, so nobody is
 * restarted mid-edit; Play installs it then without showing anything.
 */
class PlayUpdates(context: Context) : UpdateSource {
    private val manager = AppUpdateManagerFactory.create(context.applicationContext)
    private var downloaded = false
    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) downloaded = true
    }

    init { manager.registerListener(listener) }

    override suspend fun latest(): AvailableUpdate? {
        val info = manager.requestAppUpdateInfo()
        // Already fetched and waiting: nothing to ask, it installs on the way out.
        if (info.installStatus() == InstallStatus.DOWNLOADED) { downloaded = true; return null }
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return null
        if (!info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) return null
        return AvailableUpdate(info.availableVersionCode())
    }

    /** "Update": Play's own flow; the Play listing when it cannot start (iOS: openURL to the store). */
    fun start(activity: Activity) {
        manager.appUpdateInfo.addOnCompleteListener { request ->
            val started = request.isSuccessful && runCatching {
                manager.startUpdateFlow(request.result, activity, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
            }.isSuccess
            if (!started) openPlayListing(activity)
        }
    }

    /** On the way to the background: install what Play has downloaded. */
    fun installIfDownloaded() {
        if (downloaded) runCatching { manager.completeUpdate() }
    }
}

/**
 * iOS AppUpdateReminderModifier: check once the signed-in app appears, and show the card while
 * the prompt has something to say.
 */
@Composable
fun AppUpdatePrompting(prompt: AppUpdatePrompt, play: PlayUpdates) {
    val activity = LocalContext.current as? Activity ?: return
    val update by prompt.available.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { prompt.check() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { play.installIfDownloaded() }
    if (update != null) {
        AppUpdateCard(
            onUpdate = { prompt.acknowledgeUpdateOpened(); play.start(activity) },
            onDismiss = prompt::dismiss,
        )
    }
}

/**
 * iOS AppUpdateReminderCard: "A new Astrid is ready", styled like a reminder — dimmed ground, one
 * card, one clear action. Tapping the ground is "Not now", as on iOS.
 *
 * Play names the update by version code, not version name, so the body is iOS's without its
 * "Version X is available" (docs/PARITY.md). Play has no release notes to show either.
 */
@Composable
internal fun AppUpdateCard(onUpdate: () -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val colors = AstridTheme.colors
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.padding(Spacing.s24).widthIn(max = 340.dp).fillMaxWidth()
                    .shadow(20.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(colors.bgPrimary)
                    // The card itself is not the ground: a tap on it is not "Not now".
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(Spacing.s24),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s16),
            ) {
                Icon(Icons.Filled.ArrowCircleDown, null, tint = colors.accent, modifier = Modifier.size(44.dp))
                Text(
                    stringResource(R.string.update_available_title, stringResource(R.string.app_name)),
                    style = Title3, color = colors.textPrimary, textAlign = TextAlign.Center,
                )
                Text(stringResource(R.string.update_available_body), style = TextStyles.body, color = colors.textMuted, textAlign = TextAlign.Center)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.accent)
                            .clickable(role = Role.Button, onClick = onUpdate).padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.update_action), style = TextStyles.headline, color = colors.accentText) }
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button, onClick = onDismiss).padding(vertical = Spacing.s8),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.update_later), style = TextStyles.body, color = colors.textMuted) }
                }
            }
        }
    }
}

/** iOS Theme.Typography.title3: the system title2 size, semibold. */
private val Title3 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
