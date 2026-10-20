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
import android.net.Uri
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
 * shown twice.
 */
class ReminderNotifier(private val core: Core, private val post: (DueReminder) -> Unit) {
    suspend fun postDue(): Int {
        val answer = runCatching { core.run(command("remindersDue")) as? JsonObject }.getOrNull() ?: return 0
        val due = runCatching {
            CoreJson.decodeFromJsonElement(ListSerializer(DueReminder.serializer()), answer["reminders"]!!)
        }.getOrDefault(emptyList())
        for (reminder in due) {
            post(reminder)
            runCatching { core.run(command("reminderShown", "taskId" to reminder.taskId)) }
        }
        return due.size
    }

    companion object {
        const val CHANNEL = "reminders"
        /** What a notification's own snooze moves by — astrid-rules NOTIFICATION_SNOOZE_MINUTES. */
        const val SNOOZE_MINUTES = 60L
        const val ACTION_COMPLETE = "com.gracefultools.astrid.reminder.COMPLETE"
        const val ACTION_SNOOZE = "com.gracefultools.astrid.reminder.SNOOZE"
        const val EXTRA_TASK = "taskId"

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

        fun forContext(context: Context, core: Core) = ReminderNotifier(core) { reminder -> show(context, reminder) }

        fun notificationId(taskId: String) = taskId.hashCode()

        private fun show(context: Context, reminder: DueReminder) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val id = notificationId(reminder.taskId)
            val open = PendingIntent.getActivity(
                context, id,
                Intent(Intent.ACTION_VIEW, Uri.parse("astrid://task/${reminder.taskId}"), context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            fun action(name: String, code: Int) = PendingIntent.getBroadcast(
                context, id * 4 + code,
                Intent(context, ReminderActionReceiver::class.java).setAction(name).putExtra(EXTRA_TASK, reminder.taskId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(reminder.title)
                .setContentText(context.getString(R.string.reminders_due_now))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.reminders_complete), action(ACTION_COMPLETE, 1))
                .addAction(0, context.getString(R.string.reminders_snooze_hour), action(ACTION_SNOOZE, 2))
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
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
                NotificationManagerCompat.from(context).cancel(ReminderNotifier.notificationId(taskId))
                // A snooze moved the next reminder; a completion may have removed it.
                ReminderAlarm(graph.core, SystemAlarms(context)).reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}

object ReminderActions {
    suspend fun handle(core: Core, action: String?, taskId: String) {
        runCatching {
            when (action) {
                ReminderNotifier.ACTION_COMPLETE ->
                    core.run(command("completeTask", "taskId" to taskId, "completed" to true, "source" to "android-notification"))
                ReminderNotifier.ACTION_SNOOZE ->
                    core.run(command("snoozeReminder", "taskId" to taskId, "minutes" to ReminderNotifier.SNOOZE_MINUTES))
            }
        }
    }
}
