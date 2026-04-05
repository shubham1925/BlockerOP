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

/**
 * Fires exactly 24 hours after the user requests uninstall, reminding them
 * that their cooldown has expired and they can now proceed.
 */
class UninstallReadyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_ID, "Uninstall Protection", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Uninstall cooldown notifications" }
        nm.createNotificationChannel(channel)

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("Uninstall cooldown complete")
            .setContentText("Your 24-hour wait is over. Open BlockerOP to proceed with uninstall.")
            .setStyle(Notification.BigTextStyle()
                .bigText("Your 24-hour waiting period is over. Open BlockerOP and tap \"Uninstall Now\" to remove the app."))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID      = "uninstall_ready_channel"
        const val NOTIFICATION_ID = 3001
        const val REQUEST_CODE    = 9003
    }
}
