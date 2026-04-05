package com.example.blockerop.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.blockerop.MainActivity
import com.example.blockerop.R
import com.example.blockerop.data.EventLogger
import com.example.blockerop.scheduler.WeeklyReportScheduler
import java.util.Calendar

class WeeklyReportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val events = EventLogger.readAll(context)
        val weekStart = startOfDay(daysAgo = 7)
        val weekEvents = events.filter { it.timestampMs >= weekStart }

        val totalBlocked = weekEvents.count { it.wasBlocked }
        val totalAllowed = weekEvents.count { !it.wasBlocked }

        // Find worst hour of day across all time
        val allBlocked = events.filter { it.wasBlocked }
        val worstHour = if (allBlocked.isNotEmpty()) {
            val byHour = IntArray(24)
            allBlocked.forEach { e ->
                val h = Calendar.getInstance().apply { timeInMillis = e.timestampMs }
                    .get(Calendar.HOUR_OF_DAY)
                byHour[h]++
            }
            byHour.indices.maxByOrNull { byHour[it] }
        } else null

        val body = buildString {
            when {
                totalBlocked == 0 -> append("No blocked attempts this week — well done!")
                totalBlocked == 1 -> append("1 blocked attempt this week.")
                else              -> append("$totalBlocked blocked attempts this week.")
            }
            if (totalAllowed > 0) append(" ($totalAllowed in open window)")
            if (worstHour != null) append("  Peak temptation: ${formatHour(worstHour)}")
        }

        sendNotification(context, body)
        // Re-schedule for next week
        WeeklyReportScheduler.schedule(context)
    }

    private fun sendNotification(context: Context, body: String) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_ID, "Weekly Report", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Your weekly BlockerOP summary"
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("Your weekly BlockerOP report")
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun startOfDay(daysAgo: Int): Long =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun formatHour(hour: Int): String {
        val h = if (hour % 12 == 0) 12 else hour % 12
        val ampm = if (hour < 12) "AM" else "PM"
        return "$h $ampm"
    }

    companion object {
        private const val CHANNEL_ID      = "weekly_report_channel"
        private const val NOTIFICATION_ID = 2001
    }
}
