package com.mabani.mdm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Protects specific apps (the Foreman app) from ever being killed,
 * battery-restricted, force-stopped, or disabled -- without locking the
 * rest of the phone down at all. This is the actual requirement: a normal,
 * fully usable phone (WhatsApp, calls, SMS, camera, WiFi, everything) where
 * one specific app simply never gets killed in the background.
 *
 * DevicePolicyManager#setUserControlDisabledPackages (API 30+, Device Owner
 * only) is the dedicated Android API for exactly this: it stops the system
 * AND the user from force-stopping, battery-restricting, or disabling the
 * listed packages. This is a much more direct fix than Lock Task / Kiosk
 * mode, which restricts navigation on the whole device and isn't what's
 * needed here.
 */
object AppProtection {
    private const val TAG = "MdmAppProtection"

    // Add more package names here later if other apps need the same
    // never-killed guarantee. Doesn't affect any other app on the phone.
    private val PROTECTED_PACKAGES = listOf("com.mabani.foreman")

    fun apply(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Not available before Android 11 -- nothing we can do here on
            // older OS versions beyond what we already do (foreground
            // service + boot/package-replaced restart).
            return
        }
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(ctx, AdminReceiver::class.java)
            if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
            dpm.setUserControlDisabledPackages(admin, PROTECTED_PACKAGES)
        } catch (e: Exception) {
            Log.w(TAG, "setUserControlDisabledPackages failed: ${e.message}")
        }
    }
}
