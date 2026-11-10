package com.mabani.mdm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Restart the heartbeat loop both after a reboot AND after this app
        // itself gets updated (ACTION_MY_PACKAGE_REPLACED) -- an APK update
        // kills the running process, and without this nothing would bring
        // the service back until the device next reboots.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            if (Prefs.isEnrolled(context) || Prefs.enrollToken(context) != null) {
                HeartbeatService.start(context)
            }
        }
    }
}
