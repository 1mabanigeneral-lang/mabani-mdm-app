package com.mabani.mdm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (Prefs.isEnrolled(context) || Prefs.enrollToken(context) != null) {
                HeartbeatService.start(context)
            }
        }
    }
}
