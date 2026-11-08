package com.raju.aiassistant.domain.tatkal

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A daily alert before a Tatkal booking window opens ("AC 9:58", "Sleeper 10:58"). The time is what the
 * user says: nothing here assumes when IRCTC opens its quota.
 */
data class TatkalAlarm(val hour: Int, val minute: Int, val label: String) {
    /** Stable id for the scheduler: one alarm per time and class. */
    val id: Int get() = (hour * 60 + minute) * 10 + (if (label == SLEEPER) 2 else 1)

    val timeText: String get() = "%02d:%02d".format(hour, minute)
    fun spoken(hindi: Boolean = true): String =
        if (hindi) "$label Tatkal ka alarm $timeText par" else "$label Tatkal alarm at $timeText"

    companion object {
        const val AC = "AC"
        const val SLEEPER = "Sleeper"
    }
}

/**
 * The next moment the alarm should ring, strictly after [now]: today if that time is still ahead, otherwise
 * tomorrow. Local time, so a daily alarm keeps its clock time across the day.
 */
fun nextRing(alarm: TatkalAlarm, now: LocalDateTime): LocalDateTime {
    val at = LocalTime.of(alarm.hour, alarm.minute)
    val today = now.toLocalDate().atTime(at)
    return if (today.isAfter(now)) today else today.plusDays(1)
}

private val TATKAL = Regex("tatkal|tatkaal|tatakal|तत्काल|ततकाल|तात्काल")
// "9:58", "9.58", optionally with am / pm / subah / sham / raat / dopahar.
private val CLOCK = Regex("(\\d{1,2})[:.](\\d{2})\\s*(am|pm|subah|sham|shaam|raat|dopahar|सुबह|शाम|रात|दोपहर)?")

/** The class the words name: "Sleeper", "AC", or null when neither is said. */
private fun classIn(t: String): String? = when {
    Regex("sleeper|slipar|स्लीपर|स्लिपर").containsMatchIn(t) -> TatkalAlarm.SLEEPER
    Regex("\\bac\\b|ए ?सी|एसी").containsMatchIn(t) -> TatkalAlarm.AC
    else -> null
}

/**
 * Reads "Tatkal alarm 9:58 AC", "10:58 baje sleeper ke liye tatkal yaad dilao", "तत्काल 9:58 एसी".
 * Null unless the words say Tatkal and give a clear clock time (a bare "10 baje" is not guessed:
 * morning or evening?). Without AC or Sleeper the class is AC.
 */
fun parseTatkal(text: String): TatkalAlarm? {
    val t = text.lowercase()
    if (!TATKAL.containsMatchIn(t)) return null
    val (hour, minute) = clockIn(t) ?: return null
    return TatkalAlarm(hour, minute, classIn(t) ?: TatkalAlarm.AC)
}

private fun clockIn(t: String): Pair<Int, Int>? {
    val m = CLOCK.find(t) ?: return null
    var hour = m.groupValues[1].toInt()
    val minute = m.groupValues[2].toInt()
    if (minute !in 0..59) return null
    val part = m.groupValues[3]
    hour = when {
        part.isEmpty() -> if (hour in 0..23) hour else return null
        part == "am" || part.startsWith("sub") || part == "सुबह" -> if (hour in 1..12) hour % 12 else return null
        part == "pm" || part.startsWith("dop") || part == "दोपहर" -> when (hour) { 12 -> 12; in 1..11 -> hour + 12; else -> return null }
        part.startsWith("sh") || part == "शाम" -> if (hour in 1..11) hour + 12 else return null
        else -> if (hour in 1..11) hour + 12 else if (hour == 12) 0 else return null // raat / night
    }
    return hour to minute
}

/** What a Tatkal sentence asks for: set an alarm, list them, or cancel one or all. */
sealed interface TatkalCommand {
    data class Set(val alarm: TatkalAlarm) : TatkalCommand
    data object List : TatkalCommand
    /** [kind] null: the alarm at that time, of either class. */
    data class Cancel(val hour: Int, val minute: Int, val kind: String?) : TatkalCommand
    data object CancelAll : TatkalCommand
}

private val CANCEL_WORDS = Regex("cancel|band|hatao|hata do|hata do|hataye|delete|remove|रद्द|बंद|हटा|हटाओ|हटाइए|हटा दो")
private val ALL_WORDS = Regex("\\bsab\\b|\\bsaare\\b|\\bsaari\\b|\\bsabhi\\b|\\ball\\b|सब|सारे|सारी|सभी")
private val LIST_WORDS = Regex("\\blist\\b|dikhao|dikha do|kitne|kaun|kaunsa|lagaye|lagaya|suchi|सूची|दिखाओ|कितने|कौन|(?:alarm|अलार्म)\\s*(?:batao|bata do|बताओ)")

/**
 * Reads a Tatkal sentence: "tatkal alarm 9:58 AC" (set), "tatkal alarm list karo" (list),
 * "9:58 AC tatkal cancel karo" or "sab tatkal alarm hatao" (cancel). Null when it is none of these,
 * or a cancel names no time and no "sab".
 */
fun parseTatkalCommand(text: String): TatkalCommand? {
    val t = text.lowercase()
    if (!TATKAL.containsMatchIn(t)) return null
    if (CANCEL_WORDS.containsMatchIn(t)) {
        if (ALL_WORDS.containsMatchIn(t)) return TatkalCommand.CancelAll
        val (h, m) = clockIn(t) ?: return null
        return TatkalCommand.Cancel(h, m, classIn(t))
    }
    if (LIST_WORDS.containsMatchIn(t) && clockIn(t) == null) return TatkalCommand.List
    return parseTatkal(text)?.let { TatkalCommand.Set(it) }
}
