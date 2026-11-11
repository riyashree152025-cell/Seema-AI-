package com.raju.aiassistant.notifications

import android.content.Context
import com.raju.aiassistant.domain.business.BusinessStore
import com.raju.aiassistant.domain.business.Journey
import com.raju.aiassistant.domain.rail.RailPnr
import java.time.LocalDate
import java.time.ZoneId

/**
 * The waiting-ticket tracker. A PNR that was checked and is not fully confirmed (WL / RAC) with its
 * chart still open is put in the PNR diary, so PnrWatchWorker checks it again around chart time and
 * Seema says the result. A confirmed ticket, a prepared chart or a past journey is not tracked.
 */
object ChartWatch {
    internal const val PREFS = "pnr_watch"

    suspend fun onPnrChecked(context: Context, store: BusinessStore, answer: RailPnr) {
        val today = LocalDate.now(ZoneId.systemDefault())
        // What it is now is remembered either way, so the watcher announces only a real change.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(answer.snapshot.pnr, answer.snapshot.signature).apply()
        if (!com.raju.aiassistant.domain.rail.shouldWatchChart(answer, today)) return
        val known = runCatching { store.journeysFrom(today) }.getOrDefault(emptyList()).any { it.pnr == answer.snapshot.pnr }
        if (known) return
        val s = answer.snapshot
        runCatching {
            store.saveJourney(
                Journey(
                    pnr = s.pnr,
                    train = listOfNotNull(s.trainNumber, s.trainName).joinToString(" ").ifBlank { null },
                    date = answer.journeyDate!!,
                    route = answer.route,
                    note = "Waiting — chart watch",
                ),
            )
        }
    }
}
