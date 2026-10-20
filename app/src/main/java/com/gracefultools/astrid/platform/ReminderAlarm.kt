package com.gracefultools.astrid.platform

import com.gracefultools.astrid.core.movesTasks
import com.gracefultools.astrid.core.ChangeKind
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gracefultools.astrid.AstridApplication
import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.command
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant

/** Where an alarm goes. A seam so the plan is tested without AlarmManager. */
interface Alarms {
    fun set(atMillis: Long)
    fun cancel()
}

/**
 * Reminders on time with the app closed — iOS schedules each one ahead with UserNotifications.
 * Android is woken by one alarm, set for the core's `nextReminder`; when it rings the due ones are
 * posted and the next is set. The time is always the core's (reminderTime or a snooze).
 */
class ReminderAlarm(private val core: Core, private val alarms: Alarms) {
    /** Set the alarm for the next reminder, or clear it when there is none. Answers the time set. */
    suspend fun reschedule(): Long? {
        val answer = runCatching { core.run(command("nextReminder")) as? JsonObject }.getOrNull()
        val at = ((answer?.get("reminder") as? JsonObject)?.get("reminderTime") as? JsonPrimitive)?.contentOrNull
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        if (at == null) alarms.cancel() else alarms.set(at)
        return at
    }

    companion object {
        /** Keep the alarm current for the life of the process: on start and whenever the cache moves. */
        fun start(context: Context, core: Core, scope: CoroutineScope) {
            val alarm = ReminderAlarm(core, SystemAlarms(context))
            scope.launch { alarm.reschedule() }
            scope.launch {
                core.changes.collect { if (it.movesTasks || it.kind == ChangeKind.REMINDERS_DUE) alarm.reschedule() }
            }
        }
    }
}

class SystemAlarms(private val context: Context) : Alarms {
    private val manager get() = context.getSystemService(AlarmManager::class.java)
    private fun intent() = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun set(atMillis: Long) {
        val manager = manager ?: return
        // Exact when the person allows it (Settings → Alarms & reminders); otherwise the nearest the
        // system allows while idle, which is minutes rather than WorkManager's quarter hour.
        if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent())
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent())
        }
    }

    override fun cancel() { manager?.cancel(intent()) }
}

/** The alarm rang: post what is due, then set the next. Also re-arms after a reboot. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as AstridApplication).graph
        val pending = goAsync()
        graph.scope.launch {
            try {
                if (intent.action != Intent.ACTION_BOOT_COMPLETED) ReminderNotifier.forContext(context, graph.core).postDue()
                ReminderAlarm(graph.core, SystemAlarms(context)).reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
