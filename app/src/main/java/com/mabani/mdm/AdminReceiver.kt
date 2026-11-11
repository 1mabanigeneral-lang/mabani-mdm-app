package com.mabani.mdm

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

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

        // During silent zero-touch provisioning there's no one around to tap
        // through a runtime-permission dialog, so as Device Owner we grant
        // the permissions ourselves. Without this, the foreground service we
        // start below (declared with a "location" type) throws a
        // SecurityException on Android 14+ because that permission isn't
        // granted yet -- crashing the app right after install, which is what
        // makes the system provisioning wizard show "Something went wrong".
        grantRuntimePermissions(context)
        AppProtection.apply(context)

        HeartbeatService.start(context)
    }

    private fun grantRuntimePermissions(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, AdminReceiver::class.java)
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        for (permission in permissions) {
            try {
                dpm.setPermissionGrantState(
                    admin, context.packageName, permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                )
            } catch (_: Exception) {
                // Best-effort -- HeartbeatService also degrades gracefully
                // if a permission still isn't granted for some reason.
            }
        }
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        AppProtection.apply(context)
        HeartbeatService.start(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
