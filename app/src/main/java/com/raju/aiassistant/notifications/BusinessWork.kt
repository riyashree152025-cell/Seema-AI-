package com.raju.aiassistant.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.raju.aiassistant.MainActivity
import com.raju.aiassistant.R
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.domain.business.Period
import com.raju.aiassistant.domain.business.morningBriefing
import com.raju.aiassistant.domain.business.periodRange
import com.raju.aiassistant.domain.business.summarizeExpenses
import com.raju.aiassistant.ui.LaunchRequest
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Background business reminders (WorkManager). Android decides the exact minute (battery saving), so
 * "7 AM" means around 7. Everything is computed from the phone's own records — no AI call, no data
 * leaves the phone. Tapping a notification opens Seema, who then explains it in detail.
 */
object BusinessScheduler {
    private const val MORNING = "morning_briefing"
    private const val WEEKLY = "weekly_expense_report"
    private const val PNR_WATCH = "pnr_watch"

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        val now = LocalDateTime.now()
        wm.enqueueUniquePeriodicWork(
            MORNING, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MorningBriefingWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(now, nextMorning(now)), TimeUnit.MILLISECONDS)
                .build(),
        )
        // Upcoming journeys' PNRs every 30 minutes (only when a PNR service is set up; needs internet).
        wm.enqueueUniquePeriodicWork(
            PNR_WATCH, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PnrWatchWorker>(30, TimeUnit.MINUTES)
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            WEEKLY, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(now, nextSundayEvening(now)), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    internal fun nextMorning(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(LocalTime.of(7, 0))
        return if (now.isBefore(today)) today else today.plusDays(1)
    }

    internal fun nextSundayEvening(now: LocalDateTime): LocalDateTime {
        val sunday = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(LocalTime.of(20, 0))
        return if (now.isBefore(sunday)) sunday else sunday.plusWeeks(1)
    }

    private fun delayUntil(now: LocalDateTime, at: LocalDateTime) = Duration.between(now, at).toMillis().coerceAtLeast(0)
}

class MorningBriefingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as RajuApp).container
        if (!c.settingsRepository.settings.first().businessReminders) return Result.success()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val repo = c.businessRepository
        runCatching { repo.pruneOldJourneys(today) }
        val (from, to) = periodRange(Period.YESTERDAY, System.currentTimeMillis(), zone)
        val text = morningBriefing(today, repo.journeysFrom(today), repo.expensesBetween(from, to)) ?: return Result.success()
        BusinessNotifier(applicationContext).show(BusinessNotifier.MORNING_ID, "☀️ Seema: aaj ki briefing", text, LaunchRequest.ACTION_BRIEFING)
        return Result.success()
    }
}

/**
 * Checks the PNRs of journeys in the next 7 days. A confirmation, a prepared chart or any other
 * status change → a notification and, when Seema may speak, a spoken alert. The first check of a
 * PNR only remembers its status. Nothing runs without a PNR service in Settings.
 */
class PnrWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as RajuApp).container
        val s = c.settingsRepository.settings.first()
        if (!s.businessReminders) return Result.success()
        val today = LocalDate.now(ZoneId.systemDefault())
        val journeys = runCatching { c.businessRepository.journeysFrom(today) }.getOrDefault(emptyList())
            .filter { !it.date.isAfter(today.plusDays(7)) }
        if (journeys.isEmpty()) return Result.success()
        val prefs = applicationContext.getSharedPreferences("pnr_watch", Context.MODE_PRIVATE)
        for (j in journeys.distinctBy { it.pnr }) {
            val fetch = c.pnrSource.status(j.pnr)
            if (fetch is com.raju.aiassistant.domain.business.PnrFetch.NotConfigured) return Result.success()
            val snap = (fetch as? com.raju.aiassistant.domain.business.PnrFetch.Ok)?.snapshot ?: continue
            val changes = com.raju.aiassistant.domain.business.pnrChanges(prefs.getString(j.pnr, null), snap)
            prefs.edit().putString(j.pnr, snap.signature).apply()
            if (changes.isEmpty()) continue
            val text = com.raju.aiassistant.domain.business.spokenPnrAlert(snap, changes)
            BusinessNotifier(applicationContext).show(BusinessNotifier.PNR_ID_BASE + (j.pnr.hashCode() and 0xFF), "🚆 Seema: PNR ${j.pnr}", text, LaunchRequest.ACTION_BRIEFING)
            if (!s.assistantPaused && !s.safetyMode) runCatching { c.speaker.speak(text) }
        }
        return Result.success()
    }
}

class WeeklyReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as RajuApp).container
        if (!c.settingsRepository.settings.first().businessReminders) return Result.success()
        val (from, to) = periodRange(Period.WEEK, System.currentTimeMillis(), ZoneId.systemDefault())
        val expenses = c.businessRepository.expensesBetween(from, to)
        if (expenses.isEmpty()) return Result.success()
        BusinessNotifier(applicationContext).show(
            BusinessNotifier.WEEKLY_ID, "📊 Seema: hafte ka hisaab",
            summarizeExpenses(expenses, Period.WEEK, hindi = true), LaunchRequest.ACTION_WEEKLY_REPORT,
        )
        return Result.success()
    }
}

class BusinessNotifier(private val context: Context) {
    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Briefings & khata", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Morning briefing (journeys, spending) and the weekly expense report"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // POST_NOTIFICATIONS is checked right before notify(); SecurityException is still caught.
    @SuppressLint("MissingPermission")
    fun show(id: Int, title: String, text: String, action: String) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked meanwhile: nothing to show.
        }
    }

    companion object {
        const val CHANNEL_ID = "business_briefings"
        const val MORNING_ID = 4201
        const val WEEKLY_ID = 4202
        const val PNR_ID_BASE = 4300
    }
}
