package com.raju.aiassistant.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.raju.aiassistant.R

/**
 * A "कॉल काटें" button for a call Seema placed. While a phone call is on, Android gives other apps'
 * microphones silence (so the caller's voice and the call stay private) — Seema cannot hear "phone kaat
 * do" then. This notification button (also on the lock screen) ends the call in one tap instead.
 * It disappears by itself when the call ends.
 */
object CallControls {
    private const val CHANNEL_ID = "seema_call_controls"
    private const val NOTIFY_ID = 4811
    internal const val ACTION_HANG_UP = "com.raju.aiassistant.HANG_UP"

    fun showHangUp(context: Context, name: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Call controls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A button to end a call Seema placed"
                setSound(null, null)
                enableVibration(false)
            },
        )
        val hangUp = PendingIntent.getBroadcast(
            context, NOTIFY_ID,
            Intent(context, HangUpReceiver::class.java).setAction(ACTION_HANG_UP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📞 $name को कॉल")
            .setContentText("नहीं उठाया? एक टैप में काटिए।")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setTimeoutAfter(30 * 60_000L)
            .addAction(0, "कॉल काटें", hangUp)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFY_ID, n) }
    }

    fun clear(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFY_ID) }
    }
}

/** "कॉल काटें" tapped: ends the call (needs the call permission Seema already asks for) and removes the button. */
class HangUpReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION") // endCall is deprecated but is still the only way for a non-dialer app
    @android.annotation.SuppressLint("MissingPermission") // ANSWER_PHONE_CALLS is checked just above the call
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CallControls.ACTION_HANG_UP) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { context.getSystemService(TelecomManager::class.java)?.endCall() }
        }
        CallControls.clear(context)
    }
}
