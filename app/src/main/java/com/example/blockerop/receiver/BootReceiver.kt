package com.example.blockerop.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.blockerop.data.BlockerPreferences
import com.example.blockerop.scheduler.WeeklyReportScheduler
import com.example.blockerop.service.BlockerForegroundService
import com.example.blockerop.service.GuardJobService

/**
 * Restarts the blocking services after the device boots.
 * Only fires if setup was previously completed.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON") return

        if (BlockerPreferences(context).isSetupComplete) {
            BlockerForegroundService.start(context)
            GuardJobService.schedule(context)
            WeeklyReportScheduler.schedule(context)
        }
    }
}
