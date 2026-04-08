package com.example.blockerop.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.blockerop.data.BlockerPreferences
import com.example.blockerop.data.EventLogger
import com.example.blockerop.overlay.BlockOverlayManager
import com.example.blockerop.overlay.FrictionOverlayManager
import com.example.blockerop.scheduler.BlockSchedule
import com.example.blockerop.ui.isAdminActive

class BlockerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var isRunning = false
        private const val TAG = "BlockerA11y"
    }

    private lateinit var prefs: BlockerPreferences
    private var lastLoggedPkg  = ""
    private var lastLoggedTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var pendingAdminCheck: Runnable? = null

    override fun onServiceConnected() {
        isRunning = true
        prefs = BlockerPreferences(applicationContext)
        Log.d(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg       = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        // Log every window change so we can see what fires when navigating settings
        Log.d(TAG, "Window: pkg=$pkg  class=$className")

        // ── Device Admin deactivation guard ───────────────────────────────────
        if (isAdminActive(applicationContext)) {
            val reason = isDeactivatingAdminScreen(pkg, className)
            Log.d(TAG, "Admin active — deactivation check for $pkg → $reason")
            if (reason != null) {
                blockDeactivationAttempt(reason)
                return
            }
            // rootInActiveWindow may not be populated yet on the first event for a
            // settings screen. Schedule retries so we catch the case where the node
            // tree loads a moment after the window-state event fires.
            if (pkg.contains("settings", ignoreCase = true) ||
                pkg.equals("android", ignoreCase = true)) {
                pendingAdminCheck?.let { handler.removeCallbacks(it) }
                fun scheduleCheck(delayMs: Long, nextDelayMs: Long?) {
                    val r = Runnable {
                        if (isAdminActive(applicationContext)) {
                            val retryReason = isDeactivatingAdminScreen(pkg, className)
                            Log.d(TAG, "Admin retry check ($delayMs ms) for $pkg → $retryReason")
                            if (retryReason != null) {
                                blockDeactivationAttempt(retryReason)
                            } else if (nextDelayMs != null) {
                                scheduleCheck(nextDelayMs, null)
                            }
                        }
                    }
                    pendingAdminCheck = r
                    handler.postDelayed(r, delayMs)
                }
                scheduleCheck(300, 700)
            }
        }

        // ── App blocking ──────────────────────────────────────────────────────
        if (pkg !in prefs.blockedPackages) return

        val isBlocked = BlockSchedule.isCurrentlyBlocked(
            prefs.allowStartMinutes, prefs.allowEndMinutes)

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
        pendingAdminCheck?.let { handler.removeCallbacks(it) }
        pendingAdminCheck = null
        BlockOverlayManager.hide()
        FrictionOverlayManager.hide()
    }

    override fun onDestroy() {
        isRunning = false
        pendingAdminCheck?.let { handler.removeCallbacks(it) }
        pendingAdminCheck = null
        BlockOverlayManager.hide()
        FrictionOverlayManager.hide()
        super.onDestroy()
    }

    private fun blockDeactivationAttempt(reason: String) {
        Log.d(TAG, "INTERCEPTING deactivation attempt: $reason")
        pendingAdminCheck?.let { handler.removeCallbacks(it) }
        pendingAdminCheck = null
        // Click Cancel programmatically — closes the screen in the same frame.
        clickCancel()
        // HOME is a belt-and-braces fallback in case Cancel wasn't found.
        performGlobalAction(GLOBAL_ACTION_HOME)
        if (!FrictionOverlayManager.isShowing()) {
            FrictionOverlayManager.show(applicationContext)
        }
    }

    // ── Admin deactivation detection ──────────────────────────────────────────

    /**
     * Returns a non-null reason string when the current screen is the Device Admin
     * deactivation screen for BlockerOP, or null if it is safe to ignore.
     *
     * Uses two layers of detection:
     *  1. className — The DeviceAdminAdd activity is the Android screen for
     *     adding OR removing a device admin. When isAdminActive() is already true
     *     (checked by the caller), reaching this activity means the user is
     *     removing the admin. This works even when rootInActiveWindow is null.
     *  2. rootInActiveWindow text — looks for "deactivate" + "blockerop" in the
     *     node tree as a secondary signal for OEM-specific screens.
     */
    private fun isDeactivatingAdminScreen(pkg: String, className: String): String? {
        if (!pkg.contains("settings", ignoreCase = true) &&
            !pkg.equals("android", ignoreCase = true)) return null

        // Signal 1: DeviceAdminAdd is the specific activity Android opens for
        // add/remove of a device admin. Works immediately — no node tree needed.
        if (className.endsWith("DeviceAdminAdd", ignoreCase = true)) {
            Log.d(TAG, "  Signal 1 hit: className=$className (admin already active → deactivation)")
            return "className:$className"
        }

        // Signal 2: fall back to node-tree inspection for OEM variants.
        val root = rootInActiveWindow
        if (root == null) {
            Log.d(TAG, "  rootInActiveWindow is null for $pkg")
            return null
        }

        return try {
            val fullText = collectNodeText(root).lowercase()
            Log.d(TAG, "  Full text (first 300): ${fullText.take(300)}")

            // Must be about BlockerOP — prevents blocking deactivation of other admin apps.
            if (!fullText.contains("blockerop")) return null

            when {
                fullText.contains("deactivate & uninstall") -> "deactivate-and-uninstall"
                fullText.contains("deactivate")             -> "deactivate"
                else -> null
            }
        } finally {
            root.recycle()
        }
    }

    /**
     * Finds the "Cancel" button on the current screen and clicks it
     * programmatically. This dismisses the Device Admin deactivation screen
     * in the same frame — far faster than GLOBAL_ACTION_HOME.
     */
    private fun clickCancel() {
        val root = rootInActiveWindow ?: return
        try {
            root.findAccessibilityNodeInfosByText("Cancel").forEach { node ->
                Log.d(TAG, "  Clicking Cancel node: ${node.className} clickable=${node.isClickable}")
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
            }
        } finally {
            root.recycle()
        }
    }

    private fun collectNodeText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            sb.append(collectNodeText(child))
            child.recycle()
        }
        return sb.toString()
    }
}
