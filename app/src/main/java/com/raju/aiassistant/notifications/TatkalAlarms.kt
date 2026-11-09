package com.raju.aiassistant.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.raju.aiassistant.MainActivity
import com.raju.aiassistant.R
import com.raju.aiassistant.domain.tatkal.TatkalAlarm
import com.raju.aiassistant.domain.tatkal.nextRing
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Daily Tatkal alerts at the times the user set ("AC 9:58", "Sleeper 10:58"). They use the exact alarm
 * clock (AlarmManager), so they ring on time even when Seema is closed and the phone is asleep. Stored
 * on the phone only, and set again after a restart ([TatkalBootReceiver]).
 *
 * Android 14+ may not allow exact alarms until the user switches them on ("Alarms & reminders"); then
 * the alarm is set inexactly and [schedule] says so, so the user can allow it.
 */
object TatkalAlarms {
    private const val PREFS = "tatkal_alarms"
    private const val KEY = "list"
    /** v2: the notification is silent; the alarm sound plays on the alarm stream ([TatkalAlarmReceiver]). */
    private const val CHANNEL_ID = "tatkal_alarm_v2"
    private const val NOTIFY_BASE = 4500
    internal const val EXTRA_HOUR = "tatkal_hour"
    internal const val EXTRA_MINUTE = "tatkal_minute"
    internal const val EXTRA_LABEL = "tatkal_label"

    fun all(context: Context): List<TatkalAlarm> {
        val raw = runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) }.getOrNull()
        return raw.orEmpty().split(';').mapNotNull { entry ->
            val p = entry.split(':')
            if (p.size != 3) return@mapNotNull null
            TatkalAlarm(p[0].toIntOrNull() ?: return@mapNotNull null, p[1].toIntOrNull() ?: return@mapNotNull null, p[2])
        }
    }

    /** Saves the alarm and sets it. Returns false when it could only be set inexactly (see the class note). */
    fun add(context: Context, alarm: TatkalAlarm): Boolean {
        save(context, all(context).filter { it.id != alarm.id } + alarm)
        return schedule(context, alarm)
    }

    /** Cancels and forgets the alarms [matching]. Returns how many were removed. */
    fun remove(context: Context, matching: (TatkalAlarm) -> Boolean): Int {
        val gone = all(context).filter(matching)
        if (gone.isEmpty()) return 0
        val am = context.getSystemService(AlarmManager::class.java)
        gone.forEach { alarm ->
            PendingIntent.getBroadcast(
                context, alarm.id, Intent(context, TatkalAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let { pi -> am?.cancel(pi); pi.cancel() }
        }
        save(context, all(context) - gone.toSet())
        return gone.size
    }

    private fun save(context: Context, alarms: List<TatkalAlarm>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, alarms.joinToString(";") { "${it.hour}:${it.minute}:${it.label}" }).apply()
    }

    /** After a restart or an app update the alarms are gone from AlarmManager: set them again. */
    fun rescheduleAll(context: Context) {
        all(context).forEach { schedule(context, it) }
    }

    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    internal fun schedule(context: Context, alarm: TatkalAlarm, from: LocalDateTime = LocalDateTime.now()): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        val at = nextRing(alarm, from).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, TatkalAlarmReceiver::class.java)
            .putExtra(EXTRA_HOUR, alarm.hour).putExtra(EXTRA_MINUTE, alarm.minute).putExtra(EXTRA_LABEL, alarm.label)
        val pi = PendingIntent.getBroadcast(context, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return if (canExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            true
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            false
        }
    }

    internal fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("tatkal_alarm") // the first version, which played the tone as a notification sound
        val channel = NotificationChannel(CHANNEL_ID, "Tatkal alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alert before a Tatkal booking window opens"
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800)
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * The alarm tone on the alarm stream for [ms]: a notification sound is muted by Do Not Disturb and
     * silent mode, the alarm stream is not (Android lets alarms through by default).
     */
    internal fun ring(context: Context, ms: Long, done: () -> Unit) {
        val tone = runCatching {
            RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            }
        }.getOrNull()
        if (tone == null || runCatching { tone.play() }.isFailure) {
            done()
            return
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching { tone.stop() }
            done()
        }, ms)
    }

    internal fun notify(context: Context, alarm: TatkalAlarm) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, alarm.id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ Tatkal ${alarm.label} — ${alarm.timeText}")
            .setContentText("Tatkal khulne wala hai. Login aur details tayyar rakhiye.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 800, 400, 800))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFY_BASE + alarm.id % 1000, n) }
    }
}

/** Rings one Tatkal alarm, then sets its next day's ring. */
class TatkalAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra(TatkalAlarms.EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(TatkalAlarms.EXTRA_MINUTE, -1)
        val label = intent.getStringExtra(TatkalAlarms.EXTRA_LABEL) ?: return
        if (hour !in 0..23 || minute !in 0..59) return
        val alarm = TatkalAlarm(hour, minute, label)
        TatkalAlarms.schedule(context, alarm, LocalDateTime.now().plusMinutes(1))
        TatkalAlarms.notify(context, alarm)
        // A receiver may only run briefly: ring for 8 s, then let Android finish it.
        val pending = goAsync()
        TatkalAlarms.ring(context, 8_000) { pending.finish() }
    }
}

/** Sets the saved Tatkal alarms again after the phone restarts or Seema is updated. */
class TatkalBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            TatkalAlarms.rescheduleAll(context)
        }
    }
}
