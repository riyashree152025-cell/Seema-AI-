package com.raju.aiassistant.domain.memory

import com.raju.aiassistant.domain.business.Journey
import com.raju.aiassistant.domain.business.describeJourney
import com.raju.aiassistant.domain.ledger.ClientBalance
import com.raju.aiassistant.domain.reminder.Reminder
import com.raju.aiassistant.domain.sms.rupees
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Seema's memory of people and what is coming up, built only from what the user saved on the phone: the
 * udhaar book, the PNR diary, the memories they asked her to keep and their reminders. Nothing is guessed
 * and nothing is sent to the AI to make these answers. Pure, unit-tested.
 */

/** Everything saved about one person. */
data class PersonFacts(
    val name: String,
    /** What they owe (negative: what is owed to them), or null when there is no khata for them. */
    val balancePaise: Long?,
    val journeys: List<Journey>,
    val memories: List<String>,
    val reminders: List<Reminder>,
)

private val ABOUT = Regex(
    "^(.{2,30}?)\\s+(?:ke|ki|ka|के|की|का|এর|er)\\s+(?:baare|bare|bारे|बारे|বিষয়ে|সম্পর্কে)\\s*(?:mein|me|main|में)?\\s*(?:sab|saara|sara|सब|सारा|সব)\\s*(?:kuch|कुछ|কিছু)?\\s*" +
        "(?:batao|bata do|bataiye|बताओ|बता दो|बताइए|বলো|বলুন)$|" +
        "^(.{2,30}?)\\s+(?:ka|ki|का|की|এর)\\s+(?:sab|saara|sara|puri jankari|poori jankari|सब|सारा|पूरी जानकारी|সব)\\s*(?:kuch|कुछ)?\\s*(?:batao|bata do|बताओ|बता दो|বলো)$",
    RegexOption.IGNORE_CASE,
)

/**
 * "Rajesh ke baare mein sab batao" / "राजेश का सब बताओ" → "Rajesh"; null for anything else. Without "sab"
 * ("Delhi ke baare mein batao") it is a general question for the AI.
 */
fun parsePersonQuestion(text: String): String? {
    val t = text.trim().trimEnd('?', '.', '।', '!').replace(Regex("\\s+"), " ")
    if (t.length > 80) return null
    val m = ABOUT.matchEntire(t) ?: return null
    val name = (m.groups[1]?.value ?: m.groups[2]?.value)?.trim().orEmpty()
    if (name.isEmpty() || name.split(' ').size > 3 || name.any(Char::isDigit)) return null
    return name
}

/** The name as a whole word (any case): "Raj" is not found inside "Rajesh". */
fun mentions(text: String?, name: String): Boolean {
    if (text.isNullOrBlank() || name.isBlank()) return false
    val n = Regex.escape(name.trim().lowercase())
    return Regex("(?<![\\p{L}\\p{M}])$n(?![\\p{L}\\p{M}])").containsMatchIn(text.lowercase())
}

/** The facts about [name] among everything saved (journeys by passenger, note or route). */
fun factsAbout(
    name: String,
    balancePaise: Long?,
    journeys: List<Journey>,
    memories: List<String>,
    reminders: List<Reminder>,
): PersonFacts = PersonFacts(
    name = name,
    balancePaise = balancePaise,
    journeys = journeys.filter { mentions(it.passenger, name) || mentions(it.note, name) || mentions(it.route, name) },
    memories = memories.filter { mentions(it, name) },
    reminders = reminders.filter { mentions(it.text, name) },
)

private fun money(paise: Long) = "₹${rupees(paise)}"

/** One short spoken answer: money, upcoming journeys, saved notes and reminders. */
/** True when nothing at all is saved about the person (then the question goes to the AI instead). */
fun PersonFacts.isEmpty(): Boolean = balancePaise == null && journeys.isEmpty() && memories.isEmpty() && reminders.isEmpty()

fun spokenPersonBrief(f: PersonFacts, today: LocalDate, zone: ZoneId): String {
    val parts = mutableListOf<String>()
    when {
        f.balancePaise == null -> Unit
        f.balancePaise > 0 -> parts += "${f.name} से ${money(f.balancePaise)} लेने बाकी हैं"
        f.balancePaise < 0 -> parts += "${f.name} को ${money(-f.balancePaise)} देने बाकी हैं"
        else -> parts += "${f.name} का हिसाब बराबर है"
    }
    val upcoming = f.journeys.filter { !it.date.isBefore(today) }.sortedBy { it.date }
    if (upcoming.isNotEmpty()) parts += "यात्रा: " + upcoming.take(2).joinToString("; ") { (dayWord(it.date, today) + " — " + describeJourney(it)).trim() }
    f.memories.take(2).forEach { parts += "याद: $it" }
    f.reminders.filter { it.atMillis >= today.atStartOfDay(zone).toInstant().toEpochMilli() }.take(2).forEach {
        val at = Instant.ofEpochMilli(it.atMillis).atZone(zone)
        parts += "${dayWord(at.toLocalDate(), today)} ${at.hour}:${at.minute.toString().padStart(2, '0')} बजे याद दिलाना है: ${it.text}"
    }
    if (parts.isEmpty()) return "${f.name} के बारे में फ़ोन पर कुछ भी सेव नहीं है — न खाता, न यात्रा, न याद।"
    return parts.joinToString("। ") + "।"
}

private fun dayWord(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "आज"
    today.plusDays(1) -> "कल"
    else -> "${d.dayOfMonth}/${d.monthValue}"
}

/**
 * What to tell the user without being asked: today's and tomorrow's journeys (with the passenger's
 * pending money next to it), the biggest dues, and today's reminders. Null when there is nothing.
 */
fun headsUp(
    today: LocalDate,
    journeys: List<Journey>,
    balances: List<ClientBalance>,
    reminders: List<Reminder>,
    zone: ZoneId,
    includeTomorrowOnly: Boolean = false,
): String? {
    val parts = mutableListOf<String>()
    val days = if (includeTomorrowOnly) listOf(today.plusDays(1)) else listOf(today, today.plusDays(1))
    journeys.filter { it.date in days }.sortedBy { it.date }.forEach { j ->
        var line = "${dayWord(j.date, today)} यात्रा: ${describeJourney(j)}"
        val owes = j.passenger?.let { p -> balances.firstOrNull { b -> b.balancePaise > 0 && (mentions(p, b.name) || mentions(b.name, p)) } }
        if (owes != null) line += " — ${owes.name} से ${money(owes.balancePaise)} बाकी हैं"
        parts += line
    }
    if (!includeTomorrowOnly) {
        val dues = balances.filter { it.balancePaise > 0 }.sortedByDescending { it.balancePaise }.take(3)
        if (dues.isNotEmpty()) parts += "बाकी पैसा: " + dues.joinToString(", ") { "${it.name} ${money(it.balancePaise)}" }
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        reminders.filter { it.atMillis in start until end }.sortedBy { it.atMillis }.take(3).forEach {
            val at = Instant.ofEpochMilli(it.atMillis).atZone(zone)
            parts += "आज ${at.hour}:${at.minute.toString().padStart(2, '0')} बजे: ${it.text}"
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

private val HEADS_UP = Regex(
    "^(?:aaj|aj|kal|आज|कल)?\\s*(?:ka|ki|का|की)?\\s*(?:briefing|ब्रीफिंग|plan|प्लान|kya kya hai|क्या क्या है|kya hai|क्या है|update|अपडेट)\\s*(?:do|de do|batao|bata do|सुनाओ|sunao|दो|बताओ)?$|" +
        "^(?:aaj|kal|आज|कल)\\s+(?:ka|का)\\s+(?:kaam|काम|din|दिन)\\s*(?:batao|बताओ|kya hai|क्या है)?$",
    RegexOption.IGNORE_CASE,
)

/** "Aaj ki briefing do", "kal ka kya hai", "आज का प्लान बताओ". */
fun isHeadsUpQuestion(text: String): Boolean = HEADS_UP.matches(text.trim().trimEnd('?', '.', '।').replace(Regex("\\s+"), " "))
