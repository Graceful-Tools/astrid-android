package com.gracefultools.astrid.ui.settings

import android.Manifest
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gracefultools.astrid.R
import com.gracefultools.astrid.platform.TestReminder
import com.gracefultools.astrid.ui.common.coreLabel
import com.gracefultools.astrid.ui.detail.PickerSheet
import com.gracefultools.astrid.ui.detail.PickRow
import com.gracefultools.astrid.ui.detail.SearchField
import com.gracefultools.astrid.ui.theme.AstridTheme
import com.gracefultools.astrid.ui.theme.Spacing
import com.gracefultools.astrid.ui.theme.TextStyles
import com.gracefultools.astrid.ui.theme.ThemeMode
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ── Reminders ────────────────────────────────────────────────────────────────────────────────

/** iOS's ReminderSettingsView: push, email, default time, digest, quiet hours, a test. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val account = state.account
    val r = account?.reminderSettings

    // Whether the system lets this app post at all — re-read whenever the page comes back.
    var osAllows by remember { mutableStateOf(notificationsAllowed(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) osAllows = notificationsAllowed(context) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        osAllows = notificationsAllowed(context)
        vm.setReminder("enablePushReminders", granted)
    }
    var timeFor by remember { mutableStateOf<String?>(null) }
    var pickingZone by remember { mutableStateOf(false) }
    var testAlert by remember { mutableStateOf<String?>(null) }
    val testMessage = stringResource(R.string.settings_reminders_test_message)
    val deniedMessage = stringResource(R.string.settings_reminders_permission_denied)

    SettingsPage(
        stringResource(R.string.settings_reminders), Icons.Filled.Notifications, onBack,
        refreshing = state.refreshing, onRefresh = vm::refresh,
    ) {
        FormSection(stringResource(R.string.settings_reminders_push_notifications)) {
            ToggleRow(stringResource(R.string.settings_reminders_enable_push), r?.enablePushReminders ?: true) { on ->
                if (on && Build.VERSION.SDK_INT >= 33 && !hasPostPermission(context)) {
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    vm.setReminder("enablePushReminders", on)
                }
            }
            if (!osAllows) {
                FormDivider(inset = false)
                ButtonRow(stringResource(R.string.settings_reminders_open_settings)) {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            // Android: reminders at their minute need exact alarms; shown only while not allowed.
            com.gracefultools.astrid.platform.ExactAlarmRow()
        }

        FormSection(stringResource(R.string.settings_reminders_email_notifications)) {
            ToggleRow(stringResource(R.string.settings_reminders_send_email), r?.enableEmailReminders ?: false) {
                vm.setReminder("enableEmailReminders", it)
            }
        }

        FormSection(stringResource(R.string.settings_reminders_default_time)) {
            val offsets = account?.offsets.orEmpty()
            MenuPickerRow(
                stringResource(R.string.settings_reminders_when_to_remind),
                offsets.map { com.gracefultools.astrid.model.SettingChoice(it.minutes.toString(), it.titleKey) },
                (r?.defaultReminderTime ?: 0).toString(),
            ) { minutes -> vm.setReminder("defaultReminderTime", minutes.toInt()) }
        }

        FormSection(stringResource(R.string.settings_reminders_daily_digest)) {
            val digestOn = r?.enableDailyDigest ?: false
            ToggleRow(stringResource(R.string.settings_reminders_send_daily_digest), digestOn) { vm.setDailyDigest(it) }
            if (digestOn) {
                FormDivider(inset = false)
                NavRow(stringResource(R.string.settings_reminders_digest_time), value = displayTime(r?.dailyDigestTime ?: "09:00")) {
                    timeFor = "dailyDigestTime"
                }
                FormDivider(inset = false)
                NavRow(
                    stringResource(R.string.settings_reminders_timezone),
                    value = r?.dailyDigestTimezone ?: java.time.ZoneId.systemDefault().id,
                ) { pickingZone = true }
            }
        }

        FormSection(stringResource(R.string.settings_reminders_quiet_hours)) {
            val quietOn = r?.quietHoursStart != null
            ToggleRow(stringResource(R.string.settings_reminders_enable_quiet_hours), quietOn) { vm.setQuietHours(it) }
            if (quietOn) {
                FormDivider(inset = false)
                NavRow(stringResource(R.string.settings_reminders_start), value = displayTime(r?.quietHoursStart ?: "22:00")) {
                    timeFor = "quietHoursStart"
                }
                FormDivider(inset = false)
                NavRow(stringResource(R.string.settings_reminders_end), value = displayTime(r?.quietHoursEnd ?: "08:00")) {
                    timeFor = "quietHoursEnd"
                }
            }
        }

        FormSection(stringResource(R.string.settings_reminders_test)) {
            ButtonRow(
                stringResource(R.string.settings_reminders_send_test), icon = Icons.Filled.NotificationsActive,
                color = AstridTheme.colors.textPrimary,
            ) {
                testAlert = if (TestReminder.schedule(context)) testMessage else deniedMessage
            }
        }
    }

    timeFor?.let { field ->
        val current = when (field) {
            "dailyDigestTime" -> r?.dailyDigestTime ?: "09:00"
            "quietHoursStart" -> r?.quietHoursStart ?: "22:00"
            else -> r?.quietHoursEnd ?: "08:00"
        }
        TimePickDialog(current, onDismiss = { timeFor = null }) { picked ->
            timeFor = null
            // Quiet hours go as a pair, as iOS sends them: the server merges, and half a window is none.
            val changes = when (field) {
                "quietHoursStart" -> mapOf("quietHoursStart" to picked, "quietHoursEnd" to (r?.quietHoursEnd ?: "08:00"))
                "quietHoursEnd" -> mapOf("quietHoursStart" to (r?.quietHoursStart ?: "22:00"), "quietHoursEnd" to picked)
                else -> mapOf(field to picked)
            }
            vm.setReminders(changes)
        }
    }
    if (pickingZone) {
        ZonePicker(r?.dailyDigestTimezone, onDismiss = { pickingZone = false }) { zone ->
            pickingZone = false
            vm.setReminder("dailyDigestTimezone", zone)
        }
    }
    testAlert?.let { message ->
        AlertDialog(
            onDismissRequest = { testAlert = null },
            title = { Text(stringResource(R.string.settings_reminders_test_reminder)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { testAlert = null }) { Text(stringResource(R.string.actions_done)) } },
        )
    }
}

private fun hasPostPermission(context: Context) =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun notificationsAllowed(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

/** `HH:mm` from the server, shown in the device's 12/24-hour style. */
@Composable
private fun displayTime(wire: String): String {
    val context = LocalContext.current
    val time = runCatching { LocalTime.parse(wire) }.getOrNull() ?: return wire
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern, androidx.compose.ui.platform.LocalConfiguration.current.locales[0]))
}

/** iOS's hour-and-minute DatePicker, as Material's clock in an alert. Answers `HH:mm`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(initial: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val start = runCatching { LocalTime.parse(initial) }.getOrDefault(LocalTime.of(9, 0))
    val clock = rememberTimePickerState(start.hour, start.minute, android.text.format.DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(clock) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        confirmButton = {
            TextButton(onClick = { onPick("%02d:%02d".format(clock.hour, clock.minute)) }) {
                Text(stringResource(R.string.actions_set), fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/** iOS's Timezone picker: every zone identifier, with a search field on top. */
@Composable
private fun ZonePicker(current: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val zones = remember { java.time.ZoneId.getAvailableZoneIds().sorted() }
    PickerSheet(stringResource(R.string.settings_reminders_timezone), onDismiss) {
        SearchField(query, stringResource(R.string.settings_reminders_timezone)) { query = it }
        zones.filter { query.isBlank() || it.contains(query.trim(), ignoreCase = true) }.take(80).forEach { zone ->
            PickRow(zone, selected = zone == current) { onPick(zone) }
        }
    }
}

// ── Appearance ───────────────────────────────────────────────────────────────────────────────

/** iOS's AppearanceSettingsView: theme, task-details layout, list images, email-to-task. */
@Composable
fun AppearanceSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AstridTheme.colors
    SettingsPage(stringResource(R.string.settings_appearance), Icons.Filled.Brush, onBack) {
        FormSection(stringResource(R.string.settings_appearance_theme)) {
            val modes = listOf(
                ThemeMode.OCEAN to R.string.settings_theme_ocean, ThemeMode.LIGHT to R.string.settings_theme_light,
                ThemeMode.DARK to R.string.settings_theme_dark, ThemeMode.AUTO to R.string.settings_theme_auto,
            )
            Segmented(modes.map { stringResource(it.second) }, modes.indexOfFirst { it.first == state.theme }) { vm.setTheme(modes[it].first) }
        }

        // The control, not the preference, is gated: the layout chosen elsewhere is honoured regardless.
        if (state.showsTaskDisplayMode) {
            val choices = state.account?.layoutChoices.orEmpty()
            FormSection(stringResource(R.string.settings_appearance_task_details), stringResource(R.string.settings_appearance_task_details_footer)) {
                Segmented(
                    choices.map { coreLabel(it.titleKey) },
                    choices.indexOfFirst { it.value == state.account?.smartTasks?.taskDisplayMode },
                    enabled = state.serverLoaded,
                ) { vm.setTaskDisplayMode(choices[it].value) }
            }
        }

        FormSection(
            stringResource(R.string.settings_appearance_list_images_title),
            stringResource(R.string.settings_appearance_list_images_off_description),
        ) {
            ToggleRow(stringResource(R.string.settings_appearance_list_images_label), state.features.showListImages) {
                vm.setSmartTasks("showListImages", it)
            }
        }

        FormSection(stringResource(R.string.settings_appearance_email_to_task), stringResource(R.string.settings_appearance_email_to_task_footer)) {
            Column(Modifier.fillMaxWidth().padding(Spacing.s16), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Email, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.settings_inbound_task_email), style = TextStyles.body, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    Bullet(stringResource(R.string.settings_appearance_email_to_task_self), colors.accent)
                    Bullet(stringResource(R.string.settings_appearance_email_to_task_assigned), colors.accent)
                    Bullet(stringResource(R.string.settings_appearance_email_to_task_group), colors.accent)
                    HorizontalDivider(Modifier.padding(vertical = Spacing.s4), color = colors.divider)
                    Bullet(stringResource(R.string.settings_appearance_email_to_task_subject), Purple)
                    Bullet(stringResource(R.string.settings_appearance_email_to_task_body), Purple)
                }
            }
        }
    }
}

private val Purple = Color(0xFFAF52DE)

@Composable
private fun Bullet(text: String, dot: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        Text("•", color = dot, style = TextStyles.footnote)
        Text(text, style = TextStyles.footnote, color = AstridTheme.colors.textSecondary)
    }
}

/** iOS's `.pickerStyle(.segmented)`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Segmented(labels: List<String>, selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    val colors = AstridTheme.colors
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(Spacing.s12)) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = { onSelect(index) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.accent.copy(alpha = 0.15f), activeContentColor = colors.textPrimary,
                    activeBorderColor = colors.border, inactiveBorderColor = colors.border,
                    inactiveContainerColor = Color.Transparent, inactiveContentColor = colors.textPrimary,
                ),
            ) { Text(label, style = TextStyles.footnote, maxLines = 1) }
        }
    }
}

// ── Language ─────────────────────────────────────────────────────────────────────────────────

/** A language the app ships (res/values-*), by its BCP 47 tag, with iOS's native name. */
data class AppLanguage(val tag: String, @StringRes val name: Int)

/** iOS's Constants.Localization.supportedLanguages, in its order. Kept in step with res/xml/locales_config.xml. */
val AppLanguages = listOf(
    AppLanguage("en", R.string.language_english), AppLanguage("es", R.string.language_spanish),
    AppLanguage("fr", R.string.language_french), AppLanguage("de", R.string.language_german),
    AppLanguage("it", R.string.language_italian), AppLanguage("ja", R.string.language_japanese),
    AppLanguage("ko", R.string.language_korean), AppLanguage("nl", R.string.language_dutch),
    AppLanguage("pt", R.string.language_portuguese), AppLanguage("ru", R.string.language_russian),
    AppLanguage("zh-Hans", R.string.language_chinese_simplified), AppLanguage("zh-Hant", R.string.language_chinese_traditional),
)

/** The per-app language chosen (Android 13+'s LocaleManager), or null for Automatic. */
fun appLanguageTag(context: Context): String? {
    if (Build.VERSION.SDK_INT < 33) return null
    val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return null
    if (locales.isEmpty) return null
    val chosen = locales[0]
    return AppLanguages.firstOrNull { lang ->
        val want = java.util.Locale.forLanguageTag(lang.tag)
        want.language == chosen.language && (want.script.isEmpty() || want.script == chosen.script)
    }?.tag
}

@RequiresApi(33)
private fun setAppLanguage(context: Context, tag: String?) {
    context.getSystemService(LocaleManager::class.java)?.applicationLocales =
        if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}

/** iOS's LanguageSettingsView: Automatic, then the twelve languages, a check on the chosen one. */
@Composable
fun LanguageSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(appLanguageTag(context)) }
    SettingsPage(stringResource(R.string.settings_language), Icons.Filled.Language, onBack) {
        FormSection(footer = stringResource(R.string.settings_language_restart_note)) {
            CheckRow(stringResource(R.string.settings_automatic), chosen == null, subtitle = stringResource(R.string.settings_device_language)) {
                chosen = null
                if (Build.VERSION.SDK_INT >= 33) setAppLanguage(context, null)
            }
            AppLanguages.forEach { lang ->
                FormDivider(inset = false)
                val name = stringResource(lang.name)
                CheckRow(name, chosen == lang.tag) {
                    chosen = lang.tag
                    if (Build.VERSION.SDK_INT >= 33) setAppLanguage(context, lang.tag)
                }
            }
        }
    }
}
