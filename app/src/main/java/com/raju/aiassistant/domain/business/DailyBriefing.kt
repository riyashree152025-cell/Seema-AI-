package com.raju.aiassistant.domain.business

import com.raju.aiassistant.domain.ledger.ClientBalance
import com.raju.aiassistant.domain.reminder.Reminder
import com.raju.aiassistant.domain.sms.MoneyEvent
import com.raju.aiassistant.domain.sms.MoneyFlow
import com.raju.aiassistant.domain.sms.spokenRupees
import java.time.Instant
import java.time.ZoneId

/**
 * Seema's check-in (9 AM and 9 PM, or "aaj ka update do"): three short spoken sentences — today's money in and out
 * from the bank SMS, who still owes how much (udhaar / bakaya), and what is left to do today — opened warmly for the
 * time of day. Everything comes from the phone's own records; amounts are in whole rupees (spoken as words).
 */
data class Cashflow(val inPaise: Long, val inCount: Int, val outPaise: Long, val outCount: Int)

object DailyBriefing {
    /** Today's payments added up (bank SMS and the UPI apps' notes, one per payment). */
    fun cashflow(events: List<MoneyEvent>): Cashflow {
        val ins = events.filter { it.flow == MoneyFlow.IN }
        val outs = events.filter { it.flow == MoneyFlow.OUT }
        return Cashflow(ins.sumOf { it.paise }, ins.size, outs.sumOf { it.paise }, outs.size)
    }

    private fun opening(hour: Int, bengali: Boolean): String = when (hour) {
        in 4..11 -> if (bengali) "সুপ্রভাত সোনা…" else "सुप्रभात जान…"
        in 12..16 -> if (bengali) "শোনো না…" else "सुनो ना…"
        in 17..20 -> if (bengali) "দিনটা কেমন গেল?" else "दिन कैसा रहा?"
        else -> if (bengali) "সারাদিন অনেক খাটলে…" else "आज पूरा दिन बहुत भागदौड़ रही…"
    }

    /**
     * The briefing. [cash] null: the SMS could not be read (no permission), so money is left out instead of
     * claiming nothing came. [reminders]: Seema's reminders; only those still ahead today are said.
     */
    fun build(
        nowMillis: Long,
        zone: ZoneId,
        cash: Cashflow?,
        balances: List<ClientBalance>,
        reminders: List<Reminder>,
        bengali: Boolean,
    ): String {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val sentences = mutableListOf<String>()

        sentences += opening(now.hour, bengali) + " " + when {
            cash == null -> if (bengali) "আজকের ব্যাংকের হিসাব দেখতে SMS-এর অনুমতি লাগবে।" else "आज का बैंक हिसाब देखने के लिए SMS की अनुमति चाहिए।"
            cash.inCount == 0 && cash.outCount == 0 -> if (bengali) "আজ এখনো ব্যাংকে কোনো লেনদেন হয়নি।" else "आज अभी तक बैंक में कोई लेन-देन नहीं हुआ।"
            bengali -> "আজ ${spokenRupees(cash.inPaise)} টাকা এসেছে, আর ${spokenRupees(cash.outPaise)} টাকা কেটেছে।"
            else -> "आज ${spokenRupees(cash.inPaise)} रुपये आए, और ${spokenRupees(cash.outPaise)} रुपये कटे।"
        }

        val dues = balances.filter { it.balancePaise > 0 }.sortedByDescending { it.balancePaise }
        sentences += if (dues.isEmpty()) {
            if (bengali) "কারও কাছে কোনো বাকি টাকা নেই।" else "किसी का कोई बकाया नहीं है।"
        } else {
            val top = dues.take(3).joinToString(", ") { if (bengali) "${it.name}-এর ${spokenRupees(it.balancePaise)} টাকা" else "${it.name} से ${spokenRupees(it.balancePaise)} रुपये" }
            val total = spokenRupees(dues.sumOf { it.balancePaise })
            if (bengali) "বাকি আদায়: $top — মোট $total টাকা।" else "बाकी वसूली: $top — कुल $total रुपये।"
        }

        val endOfDay = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val ahead = reminders.filter { it.atMillis in nowMillis until endOfDay }.sortedBy { it.atMillis }.take(3)
        sentences += if (ahead.isEmpty()) {
            if (bengali) "আজ আর কোনো কাজ বাকি নেই, একটু বিশ্রাম নাও।" else "आज और कोई काम बाकी नहीं, थोड़ा आराम कर लो।"
        } else {
            val list = ahead.joinToString("; ") { r ->
                val t = Instant.ofEpochMilli(r.atMillis).atZone(zone)
                "${t.hour}:${t.minute.toString().padStart(2, '0')} — ${r.text}"
            }
            if (bengali) "আজকের কাজ: $list।" else "आज के काम: $list।"
        }
        return sentences.joinToString(" ")
    }

    /** 9 AM and 9 PM: the check-in times. */
    val HOURS = listOf(9, 21)

    /**
     * Seema greets him with the briefing when he unlocks the phone within [WINDOW_HOURS] after a check-in time,
     * once per check-in. [lastSpokenAt]: when it was last said. The check-in time it belongs to, or null.
     */
    fun dueCheckIn(nowMillis: Long, zone: ZoneId, lastSpokenAt: Long): Long? {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        return HOURS.map { h -> now.toLocalDate().atTime(h, 0).atZone(zone).toInstant().toEpochMilli() }
            .lastOrNull { start -> nowMillis >= start && nowMillis < start + WINDOW_HOURS * 3_600_000L }
            ?.takeIf { lastSpokenAt < it }
    }

    const val WINDOW_HOURS = 2L
}
