package com.mabani.mdm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
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
