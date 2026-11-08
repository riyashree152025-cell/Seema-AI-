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

/**
 * Reads "Tatkal alarm 9:58 AC", "10:58 baje sleeper ke liye tatkal yaad dilao", "तत्काल 9:58 एसी".
 * Null unless the words say Tatkal and give a clear clock time (a bare "10 baje" is not guessed:
 * morning or evening?). Without AC or Sleeper the label is AC.
 */
fun parseTatkal(text: String): TatkalAlarm? {
    val t = text.lowercase()
    if (!TATKAL.containsMatchIn(t)) return null
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
    val label = when {
        Regex("sleeper|slipar|स्लीपर|स्लिपर").containsMatchIn(t) -> TatkalAlarm.SLEEPER
        Regex("\\bac\\b|ए ?सी|एसी").containsMatchIn(t) -> TatkalAlarm.AC
        else -> TatkalAlarm.AC
    }
    return TatkalAlarm(hour, minute, label)
}
