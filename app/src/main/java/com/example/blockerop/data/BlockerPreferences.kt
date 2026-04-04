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

    companion object {
        private const val PREFS_NAME = "blocker_prefs"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_START = "allow_start_minutes"
        private const val KEY_END = "allow_end_minutes"

        val BLOCKED_PACKAGES = setOf(
            "com.instagram.android",
            "com.facebook.katana"
        )
    }
}
