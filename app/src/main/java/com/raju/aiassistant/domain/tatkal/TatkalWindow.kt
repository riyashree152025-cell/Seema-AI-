package com.raju.aiassistant.domain.tatkal

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * When Tatkal booking opens: one day before the journey (the journey day itself not counted), AC classes
 * at 10:00 and non-AC (Sleeper, 2S) at 11:00 — the Indian Railways rule. IRCTC can change it, so the
 * screen says where the times come from.
 */
data class TatkalWindow(val label: String, val opensAt: LocalDateTime) {
    /** The journey date this opening is for. */
    val journeyDate: LocalDate get() = opensAt.toLocalDate().plusDays(1)
}

val AC_OPENS: LocalTime = LocalTime.of(10, 0)
val NON_AC_OPENS: LocalTime = LocalTime.of(11, 0)

/** How long a window counts as "just opened" before the next day's is shown. */
private val JUST_OPENED: Duration = Duration.ofMinutes(30)

/** The AC and non-AC windows to watch from [now]: today's while ahead or just opened, else tomorrow's. */
fun tatkalWindows(now: LocalDateTime): List<TatkalWindow> = listOf(TatkalAlarm.AC to AC_OPENS, TatkalAlarm.SLEEPER to NON_AC_OPENS).map { (label, time) ->
    val today = now.toLocalDate().atTime(time)
    TatkalWindow(label, if (now.isBefore(today.plus(JUST_OPENED))) today else today.plusDays(1))
}

private val MONTHS = listOf("जनवरी", "फरवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")

/** "1 घंटे 5 मिनट में" / "खुल चुका है (10:00 से)". */
fun tatkalCountdown(w: TatkalWindow, now: LocalDateTime): String {
    val clock = "%02d:%02d".format(w.opensAt.hour, w.opensAt.minute)
    if (!now.isBefore(w.opensAt)) return "खुल चुका है ($clock से)"
    val left = Duration.between(now, w.opensAt)
    val h = left.toHours()
    val m = left.toMinutesPart() + if (left.toSecondsPart() > 0) 1 else 0
    val (hh, mm) = if (m == 60) (h + 1) to 0L else h to m.toLong()
    val parts = listOfNotNull(
        if (hh > 0) "$hh घंटे" else null,
        if (mm > 0 || hh == 0L) "$mm मिनट" else null,
    )
    return "${parts.joinToString(" ")} में, $clock बजे"
}

/** What Seema says for "Tatkal kab khulega": both windows with the journey date they are for. */
fun spokenTatkalWindows(now: LocalDateTime): String = tatkalWindows(now).joinToString(" ") { w ->
    val d = w.journeyDate
    val cls = if (w.label == TatkalAlarm.AC) "AC Tatkal" else "Sleeper (non-AC) Tatkal"
    "$cls (${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} की यात्रा के लिए): ${tatkalCountdown(w, now)}।"
}

private val TATKAL_WORD = Regex("tatkal|tatkaal|tatakal|तत्काल|ततकाल|तात्काल", RegexOption.IGNORE_CASE)
private val WHEN_WORDS = Regex(
    "khulega|khulegi|khulta|khulti|khulne|\\bkab\\b|kitne baje|kitni der|kitna time|kitna samay|countdown|opening|open hoga|खुलेगा|खुलेगी|खुलता|खुलने|कब|कितने बजे|कितनी देर|कितना समय",
    RegexOption.IGNORE_CASE,
)
private val HAS_CLOCK = Regex("\\d{1,2}[:.]\\d{2}")

/** "Tatkal kab khulega", "tatkal kitni der mein khulega" — not an alarm to set or cancel. */
fun isTatkalWhenQuestion(text: String): Boolean {
    val t = text.lowercase()
    return TATKAL_WORD.containsMatchIn(t) && WHEN_WORDS.containsMatchIn(t) && !HAS_CLOCK.containsMatchIn(t) &&
        !Regex("alarm|अलार्म|cancel|band|hatao|रद्द|बंद|हटा").containsMatchIn(t)
}
