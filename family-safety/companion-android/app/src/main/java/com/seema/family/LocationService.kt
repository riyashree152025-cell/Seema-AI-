package com.seema.family

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.Executors

/**
 * Runs ONLY while the user's "Share location" switch is ON, always with a visible notification
 * ("Sharing live location with family"). Adaptive GPS: every 15 s while moving, every 15 min when still.
 * Turning the switch OFF sends one "sharing off" message (the server then deletes the last position) and stops.
 */
class LocationService : Service() {
    private val io = Executors.newSingleThreadExecutor()
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val api by lazy { FamilyApi(this) }
    private var moving = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val speed = if (loc.hasSpeed()) loc.speed else 0f
            io.execute { api.sendLocation(loc.latitude, loc.longitude, battery(), speed, active = true) }
            val nowMoving = speed >= STILL_SPEED_MPS
            if (nowMoving != moving) {
                moving = nowMoving
                request() // switch between 15 s and 15 min
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !api.sharing || !api.joined) {
            stopSharing()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        request()
        // Sticky only while the user's switch is ON (checked again above on every restart).
        return START_STICKY
    }

    @SuppressLint("MissingPermission") // MainActivity starts this only after the permission is granted
    private fun request() {
        fused.removeLocationUpdates(callback)
        val interval = if (moving) MOVING_MS else STILL_MS
        val req = LocationRequest.Builder(if (moving) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .build()
        runCatching { fused.requestLocationUpdates(req, callback, Looper.getMainLooper()) }
    }

    private fun stopSharing() {
        fused.removeLocationUpdates(callback)
        if (api.joined) io.execute { api.sendLocation(0.0, 0.0, null, null, active = false) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        io.shutdown()
        super.onDestroy()
    }

    private fun battery(): Int? =
        (getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.seema.family.STOP"
        private const val CHANNEL = "sharing"
        private const val NOTIF_ID = 7
        private const val MOVING_MS = 15_000L
        private const val STILL_MS = 15 * 60_000L
        private const val STILL_SPEED_MPS = 1f
    }
}
