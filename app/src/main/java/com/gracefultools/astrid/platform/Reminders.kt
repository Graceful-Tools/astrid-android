package com.gracefultools.astrid.platform

import com.gracefultools.astrid.core.ChangeKind
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gracefultools.astrid.AstridApplication
import com.gracefultools.astrid.MainActivity
import com.gracefultools.astrid.R
import com.gracefultools.astrid.core.Core
import com.gracefultools.astrid.core.CoreJson
import com.gracefultools.astrid.core.command
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

/** A task asking to be remembered — astrid-rules `reminders::Reminder`. */
@Serializable
data class DueReminder(val taskId: String = "", val title: String = "", val reminderTime: String? = null, val dueDateTime: String? = null)

/**
 * Reminders as notifications (iOS: UserNotifications). The core decides what is due and when a
 * snooze moves it; this posts what it is told, then tells the core it was shown so it is not
 * shown twice. [onBatch] runs once after a batch is posted — the group summary.
 */
class ReminderNotifier(
    private val core: Core,
    private val onBatch: (List<DueReminder>) -> Unit = {},
    private val post: (DueReminder) -> Unit,
) {
    suspend fun postDue(): Int {
        val answer = runCatching { core.run(command("remindersDue")) as? JsonObject }.getOrNull() ?: return 0
        val due = runCatching {
            CoreJson.decodeFromJsonElement(ListSerializer(DueReminder.serializer()), answer["reminders"]!!)
        }.getOrDefault(emptyList())
        for (reminder in due) {
            post(reminder)
            runCatching { core.run(command("reminderShown", "taskId" to reminder.taskId)) }
        }
        if (due.isNotEmpty()) onBatch(due)
        return due.size
    }

    companion object {
        const val CHANNEL = "reminders"
        /** What a notification's own snooze moves by — astrid-rules NOTIFICATION_SNOOZE_MINUTES. */
        const val SNOOZE_MINUTES = 60L
        const val ACTION_COMPLETE = "com.gracefultools.astrid.reminder.COMPLETE"
        const val ACTION_SNOOZE = "com.gracefultools.astrid.reminder.SNOOZE"
        /** Tapping a reminder: MainActivity opens the reminder screen (one) or the triage deck (several). */
        const val ACTION_OPEN = "com.gracefultools.astrid.reminder.OPEN"
        const val EXTRA_TASK = "taskId"
        const val EXTRA_TASKS = "taskIds"
        /** Every reminder is in one group, so several due at once fold under a summary. */
        const val GROUP = "com.gracefultools.astrid.REMINDERS"
        private const val SUMMARY_ID = 0x5EED

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL, context.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        /** Listen for the core's "a reminder is due" and post it, for as long as the process lives. */
        fun start(context: Context, core: Core, scope: CoroutineScope) {
            createChannel(context)
            val notifier = forContext(context, core)
            scope.launch { core.changes.collect { if (it.kind == ChangeKind.REMINDERS_DUE) notifier.postDue() } }
            scope.launch { notifier.postDue() }
        }

        fun forContext(context: Context, core: Core) =
            ReminderNotifier(core, onBatch = { refreshSummary(context) }) { reminder -> show(context, reminder) }

        fun notificationId(taskId: String) = taskId.hashCode()

        /** The intent a tap sends: MainActivity reads [EXTRA_TASKS]. */
        fun openIntent(context: Context, taskIds: List<String>): Intent =
            Intent(context, MainActivity::class.java).setAction(ACTION_OPEN)
                .putExtra(EXTRA_TASKS, taskIds.toTypedArray())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        private fun notify(context: Context, id: Int, notification: android.app.Notification) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
        }

        private fun canPost(context: Context) = !(Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)

        /** iOS NotificationManager: "Task Due Soon", the task's title beneath, Complete and Snooze. */
        private fun show(context: Context, reminder: DueReminder) {
            if (!canPost(context)) return
            val id = notificationId(reminder.taskId)
            val open = PendingIntent.getActivity(
                context, id, openIntent(context, listOf(reminder.taskId)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            fun action(name: String, code: Int) = PendingIntent.getBroadcast(
                context, id * 4 + code,
                Intent(context, ReminderActionReceiver::class.java).setAction(name).putExtra(EXTRA_TASK, reminder.taskId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.reminders_task_due_soon))
                .setContentText(reminder.title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setGroup(GROUP)
                .addExtras(android.os.Bundle().apply { putString(EXTRA_TASK, reminder.taskId) })
                .addAction(R.drawable.ic_reminder_complete, context.getString(R.string.reminders_complete), action(ACTION_COMPLETE, 1))
                .addAction(R.drawable.ic_reminder_snooze, context.getString(R.string.reminders_snooze_hour), action(ACTION_SNOOZE, 2))
                .build()
            notify(context, id, notification)
        }

        /**
         * With two or more reminders showing, a summary folds them together; tapping it opens the
         * triage deck over all of them. With fewer, the summary goes. Its number is the badge count.
         */
        fun refreshSummary(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val showing = runCatching { manager.activeNotifications.toList() }.getOrDefault(emptyList())
                .filter { it.notification.group == GROUP && it.id != SUMMARY_ID }
            val ids = showing.mapNotNull { it.notification.extras.getString(EXTRA_TASK) }.distinct()
            if (ids.size < 2 || !canPost(context)) {
                runCatching { NotificationManagerCompat.from(context).cancel(SUMMARY_ID) }
                return
            }
            val titles = showing.mapNotNull { it.notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString() }
            val style = NotificationCompat.InboxStyle().also { inbox -> titles.forEach(inbox::addLine) }
            val open = PendingIntent.getActivity(
                context, SUMMARY_ID, openIntent(context, ids),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val summary = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.reminders_task_due_soon))
                .setContentText(titles.joinToString(", "))
                .setStyle(style)
                .setNumber(ids.size)
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            notify(context, SUMMARY_ID, summary)
        }

        /** The reminder screen dealt with these: take their notifications down. */
        fun dismiss(context: Context, taskIds: Collection<String>) {
            for (taskId in taskIds) runCatching { NotificationManagerCompat.from(context).cancel(notificationId(taskId)) }
            refreshSummary(context)
        }
    }
}

/** A notification's Complete and Snooze, through the core — completion with rollover, as anywhere. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderNotifier.EXTRA_TASK) ?: return
        val graph = (context.applicationContext as AstridApplication).graph
        val pending = goAsync()
        graph.scope.launch {
            try {
                ReminderActions.handle(graph.core, intent.action, taskId)
                ReminderNotifier.dismiss(context, listOf(taskId))
                // A snooze moved the next reminder; a completion may have removed it.
                ReminderAlarm(graph.core, SystemAlarms(context)).reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * What a reminder can be told — from a notification's buttons, the reminder screen and the triage
 * deck. Every one is a core command: completion only through `completeTask` (repeats roll over),
 * a snooze through `snoozeReminder`, "Tomorrow" through `dueDateOnDay` (which keeps a timed task's
 * time of day, as iOS's ReminderTriage.postponedDueDate does).
 */
object ReminderActions {
    suspend fun handle(core: Core, action: String?, taskId: String) {
        when (action) {
            ReminderNotifier.ACTION_COMPLETE -> complete(core, taskId, "android-notification")
            ReminderNotifier.ACTION_SNOOZE -> snooze(core, taskId, ReminderNotifier.SNOOZE_MINUTES)
        }
    }

    suspend fun complete(core: Core, taskId: String, source: String = "android-reminder"): Boolean = runCatching {
        core.run(command("completeTask", "taskId" to taskId, "completed" to true, "source" to source))
    }.isSuccess

    suspend fun snooze(core: Core, taskId: String, minutes: Long): Boolean = runCatching {
        core.run(command("snoozeReminder", "taskId" to taskId, "minutes" to minutes))
    }.isSuccess

    /** Due tomorrow, counted from [today] — the reader's calendar day — not from the old date. */
    suspend fun postpone(core: Core, taskId: String, today: java.time.LocalDate): Boolean = runCatching {
        val answer = core.run(command("dueDateOnDay", "taskId" to taskId, "day" to today.plusDays(1).toString())) as JsonObject
        val due = (answer["dueDateTime"] as? kotlinx.serialization.json.JsonPrimitive)?.let { if (it.isString) it.content else null }
        val allDay = (answer["isAllDay"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
        core.run(command("updateTask", "taskId" to taskId, "changes" to mapOf("dueDateTime" to due, "isAllDay" to allDay)))
    }.isSuccess
}
