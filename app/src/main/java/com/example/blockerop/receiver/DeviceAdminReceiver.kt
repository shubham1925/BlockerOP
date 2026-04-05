package com.example.blockerop.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Minimal Device Admin receiver.
 * Being an active Device Admin prevents the user from uninstalling BlockerOP
 * directly from Settings > Apps — the OS forces them to deactivate admin first.
 * The only intended deactivation path is through the in-app 24-hour cooldown flow.
 */
class BlockerDeviceAdminReceiver : DeviceAdminReceiver() {

    /** Called when the user manually deactivates admin via Settings > Security. */
    override fun onDisabled(context: Context, intent: Intent) {
        // If they bypassed the app flow, clear any pending uninstall request
        // so the state is consistent if they re-enable protection later.
        com.example.blockerop.data.BlockerPreferences(context).uninstallRequestedAt = 0L
    }
}
