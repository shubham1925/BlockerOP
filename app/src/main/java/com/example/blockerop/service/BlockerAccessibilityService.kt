package com.example.blockerop.service

import android.accessibilityservice.AccessibilityService
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
                Log.d(TAG, "INTERCEPTING deactivation attempt: $reason")
                // Click Cancel programmatically — this closes the screen in the
                // same frame and is much faster than GLOBAL_ACTION_HOME.
                clickCancel()
                // HOME is a belt-and-braces fallback in case Cancel wasn't found
                performGlobalAction(GLOBAL_ACTION_HOME)
                if (!FrictionOverlayManager.isShowing()) {
                    FrictionOverlayManager.show(applicationContext)
                }
                return
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
        BlockOverlayManager.hide()
        FrictionOverlayManager.hide()
    }

    override fun onDestroy() {
        isRunning = false
        BlockOverlayManager.hide()
        FrictionOverlayManager.hide()
        super.onDestroy()
    }

    // ── Admin deactivation detection ──────────────────────────────────────────

    /**
     * Returns a non-null reason string when the current screen is the Device Admin
     * deactivation screen, or null if it's safe to ignore.
     *
     * Uses two independent signals so OEM variants are covered:
     *  1. rootInActiveWindow searched for "Deactivate & uninstall" node text (covers the
     *     button label visible in the screenshot)
     *  2. rootInActiveWindow full-text contains "deactivate & uninstall" (belt-and-braces
     *     for locale variants)
     */
    private fun isDeactivatingAdminScreen(pkg: String, className: String): String? {
        // Only proceed with node-tree checks for settings packages.
        // Note: we intentionally do NOT short-circuit on className containing "DeviceAdmin"
        // because that class name appears on both the activation *and* deactivation screens,
        // which causes a false positive when the user returns from enabling Device Admin or
        // navigates back through Settings after approving the accessibility service.
        if (!pkg.contains("settings", ignoreCase = true) &&
            !pkg.equals("android", ignoreCase = true)) return null

        val root = rootInActiveWindow
        if (root == null) {
            Log.d(TAG, "  rootInActiveWindow is null for $pkg")
            return null
        }

        return try {
            // Signal 1: look for the exact button text on the Device Admin removal screen.
            // "Deactivate & uninstall" only appears on that specific screen — not on the
            // Accessibility settings page or the Device Admins list.
            val deactivateAndUninstallNodes = root.findAccessibilityNodeInfosByText("Deactivate & uninstall")
            val signal1 = deactivateAndUninstallNodes.isNotEmpty()
            deactivateAndUninstallNodes.forEach { it.recycle() }

            if (signal1) {
                Log.d(TAG, "  Signal 1 hit: found 'Deactivate & uninstall' button")
                return "findByText"
            }

            // Signal 2: exact phrase fallback (covers locale variants of the button label)
            val fullText = collectNodeText(root).lowercase()
            Log.d(TAG, "  Full text (first 200): ${fullText.take(200)}")
            if (fullText.contains("deactivate & uninstall")) {
                "fullText"
            } else null
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
