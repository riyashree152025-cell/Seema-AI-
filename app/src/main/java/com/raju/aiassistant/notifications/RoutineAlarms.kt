package com.raju.aiassistant.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.domain.routine.Routine
import com.raju.aiassistant.domain.routine.decodeRoutines
import com.raju.aiassistant.domain.routine.encodeRoutines
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The user's routines, kept on the phone (SharedPreferences), and their daily times as alarms: at the set
 * time the routine runs (unless Seema is switched off). Set again after a restart or an update.
 */
object RoutineStore {
    private const val PREFS = "seema_routines"
    private const val KEY = "list"

    fun all(context: Context): List<Routine> =
        decodeRoutines(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun save(context: Context, routine: Routine) {
        val list = all(context).filter { it.id != routine.id } + routine
        write(context, list)
        RoutineAlarms.schedule(context, routine)
    }

    fun remove(context: Context, id: Long) {
        all(context).firstOrNull { it.id == id }?.let { RoutineAlarms.cancel(context, it) }
        write(context, all(context).filter { it.id != id })
    }

    private fun write(context: Context, list: List<Routine>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encodeRoutines(list)).apply()
    }
}

object RoutineAlarms {
    internal const val EXTRA_ID = "routine_id"

    private fun pending(context: Context, r: Routine, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, (r.id % 100_000).toInt() + 7_000,
        Intent(context, RoutineAlarmReceiver::class.java).putExtra(EXTRA_ID, r.id),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The next time today or tomorrow; nothing for a routine without a time or switched off. */
    fun schedule(context: Context, r: Routine, from: LocalDateTime = LocalDateTime.now()) {
        cancel(context, r)
        val h = r.hour ?: return
        val m = r.minute ?: return
        if (!r.enabled) return
        var at = from.toLocalDate().atTime(h, m)
        if (!at.isAfter(from)) at = at.plusDays(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, r, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        if (TatkalAlarms.canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    fun cancel(context: Context, r: Routine) {
        pending(context, r, PendingIntent.FLAG_NO_CREATE)?.let { pi ->
            context.getSystemService(AlarmManager::class.java)?.cancel(pi)
            pi.cancel()
        }
    }

    fun rescheduleAll(context: Context) = RoutineStore.all(context).forEach { schedule(context, it) }
}

/** A routine's time came: run it, and set it for the next day. */
class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(RoutineAlarms.EXTRA_ID, -1)
        val routine = RoutineStore.all(context).firstOrNull { it.id == id } ?: return
        RoutineAlarms.schedule(context, routine, LocalDateTime.now().plusMinutes(1))
        runCatching { (context.applicationContext as RajuApp).container.engine.runRoutineNow(id) }
    }
}
