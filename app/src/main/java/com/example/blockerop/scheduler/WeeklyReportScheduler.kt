package com.example.blockerop.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.blockerop.receiver.WeeklyReportReceiver
import java.util.Calendar

object WeeklyReportScheduler {

    private const val REQUEST_CODE = 9002

    /** Schedules (or re-schedules) the weekly report for the next Sunday at 9 AM. */
    fun schedule(context: Context) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Advance to the next Sunday
            val dow = get(Calendar.DAY_OF_WEEK) // 1 = Sun … 7 = Sat
            val daysUntilSunday = (8 - dow) % 7
            add(Calendar.DAY_OF_YEAR, daysUntilSunday)
        }
        // If computed time is already in the past (e.g. today is Sunday after 9 AM), push one more week
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 7)
        }

        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, WeeklyReportReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }
}
