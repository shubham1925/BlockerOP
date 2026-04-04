package com.example.blockerop.scheduler

import java.util.Calendar
import java.util.concurrent.TimeUnit

object BlockSchedule {

    /**
     * Returns true if social media should be blocked right now.
     * [startMinutes] and [endMinutes] are minutes since midnight for the allowed window.
     */
    fun isCurrentlyBlocked(startMinutes: Int, endMinutes: Int): Boolean {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return currentMinutes !in startMinutes until endMinutes
    }

    /**
     * Returns seconds until the allowed window opens next (i.e. until [startMinutes]).
     */
    fun secondsUntilUnblocked(startMinutes: Int): Long {
        val now = Calendar.getInstance()
        val currentSeconds =
            now.get(Calendar.HOUR_OF_DAY) * 3600 +
            now.get(Calendar.MINUTE) * 60 +
            now.get(Calendar.SECOND)
        val allowStartSeconds = startMinutes * 60

        return if (currentSeconds < allowStartSeconds) {
            (allowStartSeconds - currentSeconds).toLong()
        } else {
            // Past the start — next window is tomorrow
            val secondsUntilMidnight = 24 * 3600 - currentSeconds
            (secondsUntilMidnight + allowStartSeconds).toLong()
        }
    }

    /** Formats minutes-since-midnight as "9:00 PM". */
    fun formatMinutes(totalMinutes: Int): String {
        val hour = totalMinutes / 60
        val minute = totalMinutes % 60
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0  -> 12
            hour > 12  -> hour - 12
            else       -> hour
        }
        return "%d:%02d %s".format(displayHour, minute, amPm)
    }

    fun formatCountdown(totalSeconds: Long): String {
        val h = TimeUnit.SECONDS.toHours(totalSeconds)
        val m = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
        val s = totalSeconds % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }
}
