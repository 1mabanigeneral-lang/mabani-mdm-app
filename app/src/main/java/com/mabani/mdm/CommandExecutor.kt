package com.mabani.mdm

import android.app.ActivityOptions
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.json.JSONObject

/**
 * Executes a command received from the backend via heartbeat.php,
 * using DevicePolicyManager (requires this app to be the active
 * Device Owner — see AdminReceiver).
 */
object CommandExecutor {
    private const val TAG = "MdmCommand"

    enum class Outcome { ACKED, FAILED }

    fun execute(ctx: Context, command: String, payload: JSONObject?): Outcome {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(ctx, AdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) {
            Log.w(TAG, "Not an active device admin — cannot execute $command")
            return Outcome.FAILED
        }

        return try {
            when (command) {
                "LOCK" -> {
                    dpm.lockNow()
                    Outcome.ACKED
                }
                "UNLOCK" -> {
                    // There is no public API to remove a screen-lock passcode on a
                    // Device-Owner-managed device. "Unlock" here means: clear any
                    // lockout triggered by too many failed attempts and reset the
                    // password requirement, so the user can set the screen unlocked.
                    if (dpm.isDeviceOwnerApp(ctx.packageName)) {
                        dpm.setPasswordQuality(admin, DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED)
                    }
                    Outcome.ACKED
                }
                "PING" -> Outcome.ACKED
                "WIPE_APP_DATA" -> {
                    val pkg = payload?.optString("package")
                    if (pkg.isNullOrBlank()) {
                        Log.w(TAG, "WIPE_APP_DATA with no package in payload")
                        Outcome.FAILED
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        dpm.clearApplicationUserData(admin, pkg, ctx.mainExecutor) { _, success ->
                            Log.i(TAG, "clearApplicationUserData($pkg) success=$success")
                        }
                        Outcome.ACKED
                    } else {
                        Outcome.FAILED
                    }
                }
                "HIDE_APP" -> {
                    val pkg = payload?.optString("package")
                    if (pkg.isNullOrBlank()) Outcome.FAILED
                    else {
                        dpm.setApplicationHidden(admin, pkg, true)
                        Outcome.ACKED
                    }
                }
                "SHOW_APP" -> {
                    val pkg = payload?.optString("package")
                    if (pkg.isNullOrBlank()) Outcome.FAILED
                    else {
                        dpm.setApplicationHidden(admin, pkg, false)
                        Outcome.ACKED
                    }
                }
                "LOCK_KIOSK" -> {
                    val pkg = payload?.optString("package")
                    if (pkg.isNullOrBlank()) {
                        Log.w(TAG, "LOCK_KIOSK with no package in payload")
                        Outcome.FAILED
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        // Whitelist only this app (plus our own, so the MDM
                        // service keeps running and we can still reach the
                        // device) and launch it pinned -- no changes needed
                        // in the target app itself, since Device Owner can
                        // force lock-task mode via ActivityOptions.
                        dpm.setLockTaskPackages(admin, arrayOf(pkg, ctx.packageName))
                        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent == null) {
                            Log.w(TAG, "No launch intent for $pkg (not installed?)")
                            Outcome.FAILED
                        } else {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            val options = ActivityOptions.makeBasic()
                            options.setLockTaskEnabled(true)
                            ctx.startActivity(launchIntent, options.toBundle())
                            Outcome.ACKED
                        }
                    } else {
                        Outcome.FAILED
                    }
                }
                "UNLOCK_KIOSK" -> {
                    // Clearing the allowed-packages list automatically kicks
                    // the device out of lock-task mode (the pinned app is no
                    // longer permitted), with no need to reach the currently
                    // foregrounded activity directly.
                    dpm.setLockTaskPackages(admin, emptyArray())
                    val ownLaunch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                    ownLaunch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ownLaunch?.let { ctx.startActivity(it) }
                    Outcome.ACKED
                }
                else -> {
                    Log.w(TAG, "Unknown command: $command")
                    Outcome.FAILED
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $command: ${e.message}")
            Outcome.FAILED
        }
    }
}
