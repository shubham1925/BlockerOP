package com.example.blockerop.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.blockerop.data.BlockerPreferences
import com.example.blockerop.data.EventLogger
import com.example.blockerop.overlay.BlockOverlayManager
import com.example.blockerop.scheduler.BlockSchedule

class BlockerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var isRunning = false
    }

    private lateinit var prefs: BlockerPreferences

    // Deduplication: TYPE_WINDOW_STATE_CHANGED can fire several times on a single
    // app launch (multiple Activities / dialogs). Only log once per 2-second window.
    private var lastLoggedPkg  = ""
    private var lastLoggedTime = 0L

    override fun onServiceConnected() {
        isRunning = true
        prefs = BlockerPreferences(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in BlockerPreferences.BLOCKED_PACKAGES) return

        val isBlocked = BlockSchedule.isCurrentlyBlocked(
            prefs.allowStartMinutes, prefs.allowEndMinutes)

        // Log only once per package per 2-second burst
        val now = System.currentTimeMillis()
        if (pkg != lastLoggedPkg || now - lastLoggedTime > 2_000) {
            lastLoggedPkg  = pkg
            lastLoggedTime = now
            EventLogger.log(applicationContext, pkg, isBlocked)
        }

        if (isBlocked) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            BlockOverlayManager.show(applicationContext)
        }
    }

    override fun onInterrupt() {
        BlockOverlayManager.hide()
    }

    override fun onDestroy() {
        isRunning = false
        BlockOverlayManager.hide()
        super.onDestroy()
    }
}
