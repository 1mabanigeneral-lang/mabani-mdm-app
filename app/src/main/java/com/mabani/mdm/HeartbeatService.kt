package com.mabani.mdm

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

/**
 * Foreground service that keeps this device "checked in" with the backend:
 * every HEARTBEAT_INTERVAL_MS it POSTs the device's location/battery to
 * heartbeat.php, runs whatever pending commands come back, and ACKs each one.
 */
class HeartbeatService : Service() {

    companion object {
        private const val CHANNEL_ID = "mdm_heartbeat"
        private const val NOTIFICATION_ID = 1
        private const val HEARTBEAT_INTERVAL_MS = 30_000L

        fun start(ctx: Context) {
            val intent = Intent(ctx, HeartbeatService::class.java)
            ContextCompat.startForegroundService(ctx, intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastLocation: Location? = null
    private var locationManager: LocationManager? = null

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            doHeartbeat()
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        startForegroundSafely()
        startLocationUpdatesIfPermitted()
    }

    // On Android 10+ we can tell startForeground() exactly which
    // service type(s) to claim. A "location" type foreground service
    // requires the location permission to already be granted or the
    // system throws a SecurityException -- so only claim it when we
    // actually have the permission; otherwise fall back to dataSync
    // only. This keeps the service (and the app) from crashing if it
    // is ever started before permissions are granted.
    private fun startForegroundSafely() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasLocation =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            var type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (hasLocation) {
                type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(heartbeatRunnable)
        handler.post(heartbeatRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(heartbeatRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    private fun startLocationUpdatesIfPermitted() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val listener = LocationListener { loc -> lastLocation = loc }
        try {
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60_000L, 50f, listener)
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60_000L, 50f, listener)
        } catch (_: Exception) {
            // Provider not available on this device — heartbeat will just omit lat/lng.
        }
    }

    private fun batteryPercent(): Int? {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (_: Exception) {
            null
        }
    }

    private fun doHeartbeat() {
        val apiKey = Prefs.apiKey(this)
        if (apiKey == null) {
            tryEnroll()
            return
        }

        Thread {
            val result = ApiClient.heartbeat(
                this, apiKey,
                lastLocation?.latitude, lastLocation?.longitude,
                batteryPercent()
            )
            if (result.ok) {
                for (cmd in ApiClient.commandsFrom(result.json)) {
                    val id = cmd.optInt("id")
                    val command = cmd.optString("command")
                    val payload = cmd.optJSONObject("payload")
                    val outcome = CommandExecutor.execute(this, command, payload)
                    val resultStr = if (outcome == CommandExecutor.Outcome.ACKED) "acked" else "failed"
                    ApiClient.ack(this, apiKey, id, resultStr)
                }
            }
        }.start()
    }

    private fun tryEnroll() {
        val token = Prefs.enrollToken(this) ?: return
        Thread {
            val result = ApiClient.enroll(this, token, Build.MODEL ?: "", Build.VERSION.RELEASE ?: "")
            if (result.ok) {
                val key = result.json?.optString("api_key")
                if (!key.isNullOrBlank()) Prefs.saveApiKey(this, key)
            }
        }.start()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID, "1MABANI MDM",
                NotificationManager.IMPORTANCE_MIN
            )
            channel.setShowBadge(false)
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("1MABANI MDM")
            .setContentText("إدارة الجهاز مفعّلة")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
