package com.raju.aiassistant.domain.calendar

/** One calendar entry of a day: start as minutes after midnight (ignored for all-day entries). */
data class CalEvent(val startMinutes: Int, val allDay: Boolean, val title: String)

sealed interface CalendarLookup {
    data class Found(val events: List<CalEvent>) : CalendarLookup
    data class NeedsPermission(val permission: String, val reason: String) : CalendarLookup
    data class Failed(val message: String) : CalendarLookup
}

private val CAL = "(?:calendar|kalendar|कैलेंडर|meeting|meetings|मीटिंग|program|programme|कार्यक्रम|schedule|शेड्यूल|appointment|appointments)"
private val DAY_WORD = "(aaj|kal|parso|today|tomorrow|आज|कल|परसों)"
private val CALENDAR_Q = Regex(
    "^$DAY_WORD (?:ka|ki|ke|का|की|के) $CAL(?: (?:batao|bata do|sunao|dikhao|kya hai|kya hain|बताओ|सुनाओ|क्या है|क्या हैं))?$|" +
        "^$DAY_WORD (?:kya kya|kaun si|kaun kaun si|kitni|क्या क्या|कौन सी|कितनी) $CAL (?:hai|hain|है|हैं)$|" +
        "^$DAY_WORD $CAL (?:mein|me|में) (?:kya|kya kya|क्या) (?:hai|hain|है|हैं)$",
    RegexOption.IGNORE_CASE,
)

/** "Aaj ki meetings batao" → 0, "kal ka calendar" → 1, "parso ka schedule" → 2; null when not a calendar question. */
fun parseCalendarQuestion(text: String): Int? {
    val t = text.trim().lowercase().trimEnd('.', '!', '?', '।').replace(Regex("\\s+"), " ")
    val m = CALENDAR_Q.matchEntire(t) ?: return null
    return when (m.groupValues.drop(1).first { it.isNotEmpty() }) {
        "aaj", "today", "आज" -> 0
        "kal", "tomorrow", "कल" -> 1
        else -> 2
    }
}

/** "आज 2 कार्यक्रम हैं: सुबह 10:30 बजे Doctor; शाम 5:00 बजे Meeting।" */
fun spokenEvents(events: List<CalEvent>, dayOffset: Int): String {
    val day = when (dayOffset) { 0 -> "आज"; 1 -> "कल"; else -> "परसों" }
    if (events.isEmpty()) return "$day calendar में कुछ नहीं है।"
    val sorted = events.sortedWith(compareBy({ !it.allDay }, { it.startMinutes }))
    val parts = sorted.take(8).map { e ->
        if (e.allDay) "पूरे दिन ${e.title}"
        else {
            val h = e.startMinutes / 60
            val part = when (h) { in 4..11 -> "सुबह"; in 12..15 -> "दोपहर"; in 16..19 -> "शाम"; else -> "रात" }
            val h12 = (h % 12).let { if (it == 0) 12 else it }
            "$part $h12:${(e.startMinutes % 60).toString().padStart(2, '0')} बजे ${e.title}"
        }
    }
    val more = if (sorted.size > 8) " और ${sorted.size - 8} और।" else "।"
    return "$day ${sorted.size} कार्यक्रम ${if (sorted.size == 1) "है" else "हैं"}: " + parts.joinToString("; ") + more
}
