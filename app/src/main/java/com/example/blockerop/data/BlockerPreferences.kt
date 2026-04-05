package com.example.blockerop.data

import android.content.Context
import android.content.SharedPreferences

class BlockerPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    /** Start of the allowed window, in minutes since midnight. Default: 9:00 PM = 1260. */
    var allowStartMinutes: Int
        get() = prefs.getInt(KEY_START, 1260)
        set(value) = prefs.edit().putInt(KEY_START, value).apply()

    /** End of the allowed window, in minutes since midnight. Default: 10:00 PM = 1320. */
    var allowEndMinutes: Int
        get() = prefs.getInt(KEY_END, 1320)
        set(value) = prefs.edit().putInt(KEY_END, value).apply()

    /** The set of package names currently being blocked. */
    var blockedPackages: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED_PKGS, DEFAULT_BLOCKED_PACKAGES)!!.toSet()
        set(value) = prefs.edit().putStringSet(KEY_BLOCKED_PKGS, value).apply()

    /** Number of consecutive clean days (zero blocked attempts during blocked hours). */
    var streakDays: Int
        get() = prefs.getInt(KEY_STREAK_DAYS, 0)
        set(value) = prefs.edit().putInt(KEY_STREAK_DAYS, value).apply()

    /** ISO date string (yyyy-MM-dd) of the last day streak was evaluated. */
    var streakLastCheckedDate: String
        get() = prefs.getString(KEY_STREAK_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_STREAK_DATE, value).apply()

    /**
     * Epoch ms when the user submitted an uninstall request (after typing the phrase).
     * 0L = no request pending.
     */
    var uninstallRequestedAt: Long
        get() = prefs.getLong(KEY_UNINSTALL_REQUESTED, 0L)
        set(value) = prefs.edit().putLong(KEY_UNINSTALL_REQUESTED, value).apply()

    companion object {
        private const val PREFS_NAME          = "blocker_prefs"
        private const val KEY_SETUP_COMPLETE  = "setup_complete"
        private const val KEY_START           = "allow_start_minutes"
        private const val KEY_END             = "allow_end_minutes"
        private const val KEY_BLOCKED_PKGS    = "blocked_packages"
        private const val KEY_STREAK_DAYS          = "streak_days"
        private const val KEY_STREAK_DATE          = "streak_last_checked"
        private const val KEY_UNINSTALL_REQUESTED  = "uninstall_requested_at"

        val DEFAULT_BLOCKED_PACKAGES = setOf(
            "com.instagram.android",
            "com.facebook.katana",
            "com.google.android.apps.photos" // kept for testing
        )
    }
}
