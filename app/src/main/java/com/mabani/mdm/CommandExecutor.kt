package com.mabani.mdm

import android.app.ActivityOptions
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
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
                    // Accept either {"packages": ["a","b",...]} (preferred,
                    // one or more apps) or the older {"package": "a"} shape.
                    val pkgs = mutableListOf<String>()
                    val pkgsArray = payload?.optJSONArray("packages")
                    if (pkgsArray != null) {
                        for (i in 0 until pkgsArray.length()) {
                            val p = pkgsArray.optString(i)
                            if (p.isNotBlank()) pkgs.add(p)
                        }
                    }
                    if (pkgs.isEmpty()) {
                        val single = payload?.optString("package")
                        if (!single.isNullOrBlank()) pkgs.add(single)
                    }

                    // Phone/SMS/Camera package names are OEM-specific (differ
                    // between the G-Tab and Honor devices in the fleet), so
                    // instead of hardcoding guesses we resolve whichever apps
                    // are this device's own default dialer/SMS/camera at
                    // runtime and always include them -- this way the same
                    // backend command produces a correct whitelist on every
                    // phone model without per-device configuration.
                    pkgs.addAll(resolveBuiltInAppWhitelist(ctx))

                    if (pkgs.isEmpty()) {
                        Log.w(TAG, "LOCK_KIOSK with no packages in payload")
                        Outcome.FAILED
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        // Whitelist the chosen apps (plus our own, so the MDM
                        // service keeps running and we can still reach the
                        // device) and launch the first one pinned -- no
                        // changes needed in the target apps themselves, since
                        // Device Owner can force lock-task mode via
                        // ActivityOptions.
                        val allowed = (pkgs + ctx.packageName).distinct().toTypedArray()
                        dpm.setLockTaskPackages(admin, allowed)
                        try {
                            // HOME lets the user switch between the allowed
                            // apps via a restricted launcher instead of being
                            // stuck on just the first one; OVERVIEW/NOTIFICATIONS
                            // keep recents and the notification shade usable
                            // for the same reason. Nothing outside the
                            // whitelisted apps is reachable from any of these.
                            dpm.setLockTaskFeatures(
                                admin,
                                DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                                    DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
                                    DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "setLockTaskFeatures failed: ${e.message}")
                        }
                        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(pkgs[0])
                        if (launchIntent == null) {
                            Log.w(TAG, "No launch intent for ${pkgs[0]} (not installed?)")
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

    // Finds this specific device's default phone/dialer, default SMS, and
    // default camera apps, so Kiosk mode always lets the user call, text,
    // and take photos -- regardless of which OEM app provides those on this
    // particular phone model. Each lookup is independently best-effort: if
    // one fails or finds nothing, the others still proceed.
    private fun resolveBuiltInAppWhitelist(ctx: Context): List<String> {
        val found = mutableListOf<String>()

        try {
            val telecom = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val dialerPkg = telecom?.defaultDialerPackage
            if (!dialerPkg.isNullOrBlank()) found.add(dialerPkg)
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve default dialer: ${e.message}")
        }

        try {
            val smsPkg = Telephony.Sms.getDefaultSmsPackage(ctx)
            if (!smsPkg.isNullOrBlank()) found.add(smsPkg)
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve default SMS app: ${e.message}")
        }

        try {
            // Every camera app registers this launcher category; resolving
            // it returns whichever one the system treats as the default.
            val cameraIntent = Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CAMERA")
            val resolved = ctx.packageManager.resolveActivity(cameraIntent, 0)
            val cameraPkg = resolved?.activityInfo?.packageName
            if (!cameraPkg.isNullOrBlank()) found.add(cameraPkg)
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve default camera app: ${e.message}")
        }

        return found
    }
}
