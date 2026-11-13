package com.raju.aiassistant.domain.reminder

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One reminder Seema keeps herself: when (epoch millis) and what to say. */
data class Reminder(val id: Long, val atMillis: Long, val text: String)

sealed interface ReminderCommand {
    /** [at] is local time; [text] is what to remind of (never empty). */
    data class Add(val at: LocalDateTime, val text: String) : ReminderCommand
    data object List : ReminderCommand
    data object CancelLast : ReminderCommand
    data object CancelAll : ReminderCommand
}

private const val REMIND = "(?:yaad dila(?:na|o|iye|iyega| do| dena)?|remind(?: me| kar(?:na|o| do| dena)?)?|reminder (?:laga(?:o| do| dena)?|set kar(?:o| do)?)|याद दिला(?:ना|ओ|इए|इएगा| दो| देना)?|रिमाइंडर लगा(?:ओ| दो| देना)?)"
private val HAS_REMIND = Regex(REMIND, RegexOption.IGNORE_CASE)
private val RELATIVE = Regex("(\\d{1,3})\\s*(minute|minutes|min|mins|मिनट|ghante|ghanta|ghanton|hour|hours|घंटे|घंटा)\\s*(?:baad|bad|mein|me|में|बाद|ke baad|के बाद)", RegexOption.IGNORE_CASE)
private const val PART = "(subah|subha|savere|morning|सुबह|सवेरे|dopahar|dopehar|दोपहर|shaam|sham|evening|शाम|raat|rat|night|रात)"
private val DAY = Regex("(?<![\\p{L}])(aaj|kal|parso|parson|today|tomorrow|आज|कल|परसों)(?![\\p{L}])", RegexOption.IGNORE_CASE)
private val CLOCK = Regex("(?:$PART\\s+)?(?:ko\\s+|को\\s+)?(\\d{1,2})(?:[:.](\\d{2}))?\\s*(?:baje|bje|बजे)(?:\\s+$PART)?", RegexOption.IGNORE_CASE)
private val FILLERS = Regex("^(?:(?:mujhe|mujhko|muje|please|zara|मुझे|ज़रा|जरा|ko|को|ki|कि|ke liye|के लिए|ka|का)\\s+)+|(?:\\s+(?:ka|ki|ke liye|का|की|के लिए|ko|को|please))+$", RegexOption.IGNORE_CASE)
private val LIST = Regex("^(?:(?:sab|saare|sabhi|सब|सारे|सभी) )?(?:mere |mera |मेरे )?(?:reminder|reminders|रिमाइंडर) (?:batao|bata do|dikhao|list karo|list|kya hain|kya hai|बताओ|दिखाओ|क्या हैं)$|^(?:kaun se|kitne|कौन से|कितने) (?:reminder|रिमाइंडर) (?:hain|lage hain|हैं|लगे हैं)$", RegexOption.IGNORE_CASE)
private val CANCEL_ALL = Regex("^(?:sab|saare|sabhi|all|सब|सारे|सभी) (?:reminder|reminders|रिमाइंडर) (?:hatao|hata do|cancel karo|cancel|delete karo|band karo|हटाओ|हटा दो|बंद करो)$", RegexOption.IGNORE_CASE)
private val CANCEL_LAST = Regex("^(?:aakhri|akhri|last|pichla|आख़िरी|आखिरी|पिछला) (?:reminder|रिमाइंडर) (?:hatao|hata do|cancel karo|cancel|delete karo|हटाओ|हटा दो)$", RegexOption.IGNORE_CASE)

/**
 * Reads "10 minute baad yaad dilana ki dawai leni hai", "kal subah 8 baje yaad dilao bill bharna hai",
 * "shaam 5:30 baje meeting yaad dilana", and "reminder batao" / "sab reminder hatao".
 *
 * Null when the sentence is not about a reminder, has no clear time (a bare "8 baje": morning or
 * evening? is not guessed), or says nothing to be reminded of. A clock time already past today moves
 * to tomorrow unless a day was said.
 */
fun parseReminder(text: String, now: LocalDateTime): ReminderCommand? {
    val t = text.trim().trimEnd('.', '!', '?', '।').replace(Regex("\\s+"), " ")
    if (t.length > 160) return null
    if (LIST.matches(t)) return ReminderCommand.List
    if (CANCEL_ALL.matches(t)) return ReminderCommand.CancelAll
    if (CANCEL_LAST.matches(t)) return ReminderCommand.CancelLast
    if (!HAS_REMIND.containsMatchIn(t)) return null

    var rest = t
    val at: LocalDateTime
    val rel = RELATIVE.find(t)
    if (rel != null) {
        val n = rel.groupValues[1].toInt()
        val unit = rel.groupValues[2].lowercase()
        val minutes = if (unit.startsWith("m") || unit.startsWith("मि")) n else n * 60
        if (minutes !in 1..7 * 24 * 60) return null
        at = now.plusMinutes(minutes.toLong()).withSecond(0).withNano(0)
        rest = rest.replace(rel.value, " ")
    } else {
        val clock = CLOCK.find(t) ?: return null
        val part = clock.groupValues[1].ifEmpty { clock.groupValues[4] }.lowercase()
        val h = clock.groupValues[2].toInt()
        val m = clock.groupValues[3].ifEmpty { "0" }.toInt()
        if (m !in 0..59) return null
        val hour = when {
            part.isEmpty() -> if (h in 13..23) h else return null // "8 baje": morning or evening? not guessed
            part.startsWith("sub") || part.startsWith("sav") || part == "morning" || part == "सुबह" || part == "सवेरे" -> if (h in 1..12) h % 12 else return null
            part.startsWith("dop") || part == "दोपहर" -> when (h) { 12 -> 12; in 1..5 -> h + 12; else -> return null }
            part.startsWith("sh") || part == "evening" || part == "शाम" -> if (h in 1..11) h + 12 else return null
            else -> when (h) { in 7..11 -> h + 12; 12 -> 0; in 1..5 -> h; else -> return null } // raat / night
        }
        val day = DAY.find(t)
        val plusDays = when (day?.value?.lowercase()) {
            null -> null
            "aaj", "today", "आज" -> 0L
            "kal", "tomorrow", "कल" -> 1L
            else -> 2L
        }
        var candidate = now.toLocalDate().plusDays(plusDays ?: 0).atTime(hour, m)
        if (plusDays == null && !candidate.isAfter(now)) candidate = candidate.plusDays(1)
        if (!candidate.isAfter(now)) return null // "aaj subah 8 baje" said in the evening
        at = candidate
        rest = rest.replace(clock.value, " ")
        if (day != null) rest = rest.replace(day.value, " ")
    }
    rest = HAS_REMIND.replace(rest, " ").replace(Regex("\\s+"), " ").trim()
    repeat(3) { rest = FILLERS.replace(rest, "").trim() }
    if (rest.length < 2 || !rest.any { it.isLetter() }) return null
    return ReminderCommand.Add(at, rest)
}

/** "आज शाम 5:30 बजे" / "कल सुबह 8:00 बजे" / "12 Nov, 8:00 बजे" for speaking a reminder's time. */
fun spokenReminderTime(at: LocalDateTime, now: LocalDateTime): String {
    val day = when (at.toLocalDate()) {
        now.toLocalDate() -> "आज"
        now.toLocalDate().plusDays(1) -> "कल"
        else -> at.format(DateTimeFormatter.ofPattern("d MMM"))
    }
    val part = when (at.hour) { in 4..11 -> "सुबह"; in 12..15 -> "दोपहर"; in 16..19 -> "शाम"; else -> "रात" }
    val h12 = (at.hour % 12).let { if (it == 0) 12 else it }
    return "$day $part $h12:${at.minute.toString().padStart(2, '0')} बजे"
}
