package com.example.blockerop.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.blockerop.MainActivity
import com.example.blockerop.R
import com.example.blockerop.data.BlockerPreferences
import com.example.blockerop.overlay.BlockOverlayManager
import com.example.blockerop.scheduler.BlockSchedule

/**
 * Persistent foreground service that:
 *  1. Keeps a non-dismissible notification so the OS doesn't kill the process.
 *  2. Polls UsageStatsManager every 500 ms as a fallback if the Accessibility
 *     Service is killed or not yet enabled.
 *  3. Hides the overlay when the 9–10 PM window opens.
 *  4. Schedules a self-restart via AlarmManager if removed from recents.
 */
class BlockerForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleRestart()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        pollRunnable?.let { handler.removeCallbacks(it) }
        BlockOverlayManager.hide()
        scheduleRestart()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Polling ───────────────────────────────────────────────────────────────

    private fun startPolling() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val prefs = BlockerPreferences(applicationContext)

        val runnable = object : Runnable {
            override fun run() {
                if (BlockSchedule.isCurrentlyBlocked(prefs.allowStartMinutes, prefs.allowEndMinutes)) {
                    // Only use UsageStats as a fallback when Accessibility is not running
                    if (!BlockerAccessibilityService.isRunning) {
                        checkForegroundViaUsageStats(usm)
                    }
                } else {
                    // Allowed window opened — make sure overlay is gone
                    if (BlockOverlayManager.isShowing()) {
                        BlockOverlayManager.hide()
                    }
                }
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
        pollRunnable = runnable
        handler.post(runnable)
    }

    private fun checkForegroundViaUsageStats(usm: UsageStatsManager) {
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 3000L, now)
        val foregroundPkg = stats
            ?.filter { it.lastTimeUsed > now - 3000L }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName ?: return

        if (foregroundPkg in BlockerPreferences.BLOCKED_PACKAGES) {
            if (!BlockOverlayManager.isShowing()) {
                BlockOverlayManager.show(applicationContext)
            }
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BlockerOP Active",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "BlockerOP is actively blocking social media apps"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("BlockerOP Active")
            .setContentText("Social media blocking is on")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    // ── Restart scheduling ────────────────────────────────────────────────────

    private fun scheduleRestart() {
        val pi = PendingIntent.getService(
            this, RESTART_REQUEST_CODE,
            Intent(this, BlockerForegroundService::class.java),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 2000L,
                pi
            )
        } else {
            am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000L, pi)
        }
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "blocker_op_channel"
        private const val POLL_INTERVAL_MS = 500L
        private const val RESTART_REQUEST_CODE = 9001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BlockerForegroundService::class.java))
        }
    }
}
