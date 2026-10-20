package com.gracefultools.astrid.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gracefultools.astrid.R

/**
 * iOS's NotificationManager.scheduleTestReminder: a local notification five seconds from now, so a
 * person can see what a reminder looks like. Nothing goes to the server; it is the device's own
 * check that notifications reach them.
 */
object TestReminder {
    private const val ID = 0x7E57
    private const val DELAY_MS = 5_000L

    /** False when this app may not post notifications (iOS throws permissionDenied). */
    fun schedule(context: Context): Boolean {
        val app = context.applicationContext
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted || !NotificationManagerCompat.from(app).areNotificationsEnabled()) return false
        ReminderNotifier.createChannel(app)
        val notification = NotificationCompat.Builder(app, ReminderNotifier.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(app.getString(R.string.settings_reminders_test_reminder))
            .setContentText(app.getString(R.string.settings_reminders_test_body, app.getString(R.string.app_name)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { NotificationManagerCompat.from(app).notify(ID, notification) }
        }, DELAY_MS)
        return true
    }
}
