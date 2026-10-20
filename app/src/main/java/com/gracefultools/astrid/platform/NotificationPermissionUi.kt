package com.gracefultools.astrid.platform

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.Preferences
import com.gracefultools.astrid.R
import com.gracefultools.astrid.ui.theme.AstridTheme

/**
 * Where the permission stands, as near to iOS's three states as Android lets us tell: granted;
 * never asked (or "ask again" still allowed); refused for good, which only Settings can undo.
 */
fun notificationStatus(activity: Activity, preferences: Preferences): NotificationStatus {
    if (Build.VERSION.SDK_INT < 33) {
        return if (NotificationManagerCompat.from(activity).areNotificationsEnabled()) NotificationStatus.AUTHORIZED else NotificationStatus.DENIED
    }
    val granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return when {
        granted && NotificationManagerCompat.from(activity).areNotificationsEnabled() -> NotificationStatus.AUTHORIZED
        granted -> NotificationStatus.DENIED
        !preferences.askedSystemPermission -> NotificationStatus.NOT_DETERMINED
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS) -> NotificationStatus.NOT_DETERMINED
        else -> NotificationStatus.DENIED
    }
}

/** The app's page in system Settings, for notifications (iOS PlatformApplication.openAppSettings). */
fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * iOS's two alerts (AstridApp / TaskDetailViewNew): "Enable Push Notifications" before the system
 * dialog, "Notifications Disabled" with Open Settings once refused. Checked shortly after the
 * signed-in app appears (iOS: on sign-in, after a second) and whenever a due date is set.
 */
@Composable
fun NotificationPrompting(prompt: NotificationPrompt, preferences: Preferences) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val showing by prompt.showing.collectAsStateWithLifecycle()
    val requests by prompt.requests.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1_000)
        prompt.check(notificationStatus(activity, preferences))
    }
    LaunchedEffect(requests) { if (requests > 0) prompt.check(notificationStatus(activity, preferences)) }

    when (showing) {
        PromptDecision.NONE -> Unit
        PromptDecision.ASK -> AlertDialog(
            onDismissRequest = prompt::dismiss,
            title = { Text(stringResource(R.string.notifications_enable_push)) },
            text = { Text(stringResource(R.string.notifications_enable_message)) },
            dismissButton = { TextButton(onClick = prompt::dismiss) { Text(stringResource(R.string.notifications_not_now)) } },
            confirmButton = {
                TextButton(onClick = {
                    prompt.dismiss()
                    if (Build.VERSION.SDK_INT >= 33) {
                        preferences.askedSystemPermission = true
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                }) { Text(stringResource(R.string.notifications_enable)) }
            },
        )
        PromptDecision.OPEN_SETTINGS -> AlertDialog(
            onDismissRequest = prompt::dismiss,
            title = { Text(stringResource(R.string.notifications_disabled)) },
            text = { Text(stringResource(R.string.notifications_disabled_message)) },
            dismissButton = { TextButton(onClick = prompt::dismiss) { Text(stringResource(R.string.notifications_not_now)) } },
            confirmButton = {
                TextButton(onClick = { prompt.dismiss(); openNotificationSettings(context) }) {
                    Text(stringResource(R.string.notifications_open_settings))
                }
            },
        )
    }
}

/** Whether reminders can ring on the minute (Android 12+ asks separately: Alarms & reminders). */
fun canScheduleExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() != false

/**
 * A Settings row, shown only while exact alarms are not allowed, that opens the system's
 * "Alarms & reminders" page. Android only: iOS schedules each reminder itself (AATD-54).
 */
@Composable
fun ExactAlarmRow() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = canScheduleExactAlarms(context) }
    if (allowed || Build.VERSION.SDK_INT < 31) return
    val colors = AstridTheme.colors
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }.padding(vertical = 6.dp),
    ) {
        Text(stringResource(R.string.notifications_exact_alarms), color = colors.textPrimary, fontSize = 17.sp)
        Text(stringResource(R.string.notifications_exact_alarms_hint), color = colors.textSecondary, fontSize = 13.sp)
    }
}
