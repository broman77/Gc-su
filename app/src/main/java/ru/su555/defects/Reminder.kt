package ru.su555.defects

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.LocalDate
import java.util.Calendar

object ReminderScheduler {
    private const val PREFS = "reminder_prefs"
    private const val KEY_ENABLED = "daily_enabled"
    private const val REQUEST_CODE = 555

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()

        if (enabled) schedule(context) else cancel(context)
    }

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pending
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!ReminderScheduler.isEnabled(context)) return

        if (
            Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val defects = LocalStore(context).activeDefects()
        if (defects.isEmpty()) return

        val today = LocalDate.now()
        val dashboard = defects.dashboard(today)
        val dueToday = defects.count {
            !it.isClosed && it.dueDate == today
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "daily_summary"
        notificationManager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "Ежедневная сводка",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Просрочки и сроки по квартирам"
            }
        )

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = buildString {
            append("Просрочка: ")
            append(dashboard.apartmentsWithOverdue)
            append(" кв.")
            if (dueToday > 0) {
                append(" · срок сегодня: ")
                append(dueToday)
                append(" замеч.")
            }
            append(" · в работе: ")
            append(dashboard.apartmentsWithOpen)
            append(" кв.")
        }

        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Устранение замечаний")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        notificationManager.notify(555, notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && ReminderScheduler.isEnabled(context)) {
            ReminderScheduler.schedule(context)
        }
    }
}
