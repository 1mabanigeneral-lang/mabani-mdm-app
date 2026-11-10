package com.mabani.mdm

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device Admin / Device Owner receiver. Android calls onEnabled() right
 * after this app becomes Device Owner — either through QR/zero-touch
 * provisioning, or after `adb shell dpm set-device-owner` during testing.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // Pull the enroll_token out of the provisioning extras bundle, if present
        // (it was embedded in the QR code as PROVISIONING_ADMIN_EXTRAS_BUNDLE).
        val extras = intent.getBundleExtra("android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE")
        val token = extras?.getString("enroll_token")
        if (!token.isNullOrBlank()) {
            Prefs.saveEnrollToken(context, token)
        }
        HeartbeatService.start(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        HeartbeatService.start(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
