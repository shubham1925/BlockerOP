package com.example.blockerop.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object StreakManager {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Must be called once per app open (from onResume).
     * Checks whether yesterday was a "clean" day (zero blocked attempts) and
     * increments or resets the streak accordingly.
     * Does nothing if already called today.
     */
    fun refresh(prefs: BlockerPreferences, events: List<AppOpenEvent>) {
        val todayStr     = dayStr(0)
        val yesterdayStr = dayStr(1)
        if (prefs.streakLastCheckedDate == todayStr) return

        val yStart = startOfDay(daysAgo = 1)
        val yEnd   = startOfDay(daysAgo = 0)
        val hadViolations = events.any { it.timestampMs in yStart until yEnd && it.wasBlocked }

        val last = prefs.streakLastCheckedDate
        prefs.streakDays = when {
            last == yesterdayStr && !hadViolations -> prefs.streakDays + 1
            last == yesterdayStr &&  hadViolations -> 0
            last.isEmpty()        && !hadViolations -> 1
            else                                   -> 0   // gap or first violation
        }
        prefs.streakLastCheckedDate = todayStr
    }

    private fun dayStr(daysAgo: Int): String =
        dateFmt.format(Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }.time)

    private fun startOfDay(daysAgo: Int): Long =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
