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
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.raju.aiassistant.MainActivity
import com.raju.aiassistant.R
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.domain.reminder.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Seema's own reminders ("10 minute baad yaad dilana ki …"). Kept on the phone only; each one is an
 * exact alarm, so it comes on time with the app closed, and they are set again after a restart.
 * When it is due: a notification, the alarm tone for a moment, and — unless Seema is switched off —
 * she says it out loud.
 */
object Reminders {
    private const val PREFS = "seema_reminders"
    private const val KEY = "list"
    private const val CHANNEL_ID = "seema_reminders"
    private const val NOTIFY_BASE = 4700
    internal const val EXTRA_ID = "reminder_id"
    private const val SEP = "\u001F"

    fun all(context: Context): List<Reminder> {
        val raw = runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()) }.getOrNull().orEmpty()
        return raw.mapNotNull { line ->
            val p = line.split(SEP, limit = 3)
            if (p.size != 3) null else Reminder(p[0].toLongOrNull() ?: return@mapNotNull null, p[1].toLongOrNull() ?: return@mapNotNull null, p[2])
        }.sortedBy { it.atMillis }
    }

    /** Saves and sets a reminder. Returns it, with [exact] false when Android only allowed an inexact alarm. */
    fun add(context: Context, atMillis: Long, text: String): Pair<Reminder, Boolean> {
        val r = Reminder(System.currentTimeMillis(), atMillis, text.trim().take(200).replace(SEP, " "))
        save(context, all(context) + r)
        return r to schedule(context, r)
    }

    fun remove(context: Context, matching: (Reminder) -> Boolean): Int {
        val gone = all(context).filter(matching)
        if (gone.isEmpty()) return 0
        val am = context.getSystemService(AlarmManager::class.java)
        gone.forEach { r -> pending(context, r, PendingIntent.FLAG_NO_CREATE)?.let { am?.cancel(it); it.cancel() } }
        save(context, all(context) - gone.toSet())
        return gone.size
    }

    /** After a restart or an update: set what is still ahead; what was missed while the phone was off is shown now. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        all(context).forEach { r -> if (r.atMillis > now) schedule(context, r) else fire(context, r) }
    }

    private fun save(context: Context, list: List<Reminder>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY, list.map { "${it.id}$SEP${it.atMillis}$SEP${it.text}" }.toSet()).apply()
    }

    private fun pending(context: Context, r: Reminder, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, (r.id % Int.MAX_VALUE).toInt(),
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, r.id),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun schedule(context: Context, r: Reminder): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        val pi = pending(context, r, PendingIntent.FLAG_UPDATE_CURRENT) ?: return false
        return if (TatkalAlarms.canExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.atMillis, pi); true
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.atMillis, pi); false
        }
    }

    /** Shows the reminder and forgets it. */
    internal fun fire(context: Context, r: Reminder) {
        save(context, all(context).filter { it.id != r.id })
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders you asked Seema for"
                enableVibration(true)
            },
        )
        val open = PendingIntent.getActivity(
            context, (r.id % Int.MAX_VALUE).toInt(), Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ याद दिलाना था")
            .setContentText(r.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(r.text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFY_BASE + (r.id % 500).toInt(), n) }
    }
}

/** A reminder is due: notification, a short alarm tone, and Seema says it (unless she is switched off). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Reminders.EXTRA_ID, -1)
        val r = Reminders.all(context).firstOrNull { it.id == id } ?: return
        Reminders.fire(context, r)
        val pending = goAsync()
        TatkalAlarms.ring(context, 2_500) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val c = (context.applicationContext as? RajuApp)?.container
                    val s = c?.currentSettings?.value
                    if (c != null && s != null && !s.assistantPaused && !s.safetyMode) {
                        withTimeoutOrNull(7_000) { runCatching { c.speaker.speak("याद दिलाना था: ${r.text}") } }
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
