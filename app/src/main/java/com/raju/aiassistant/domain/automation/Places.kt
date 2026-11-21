package com.raju.aiassistant.domain.automation

import com.raju.aiassistant.domain.reminder.Reminder
import java.time.Instant
import java.time.ZoneId

/**
 * "Is jagah ko dukaan naam se yaad rakho", "ye jagah ghar hai", "এই জায়গাটা দোকান নামে মনে রাখো": this spot is saved as a named
 * place (on the phone only). Arriving there later, Seema says "दुकान पहुँच गए — आज बाकी काम: …" (today's reminders still ahead).
 */
object Places {
    private val SAVE = listOf(
        Regex("^(?:seema[, ]+)?(?:is|iss|ye|yeh|इस|ये|यह)\\s+(?:jagah|jaga|location|जगह|लोकेशन)\\s+(?:ko\\s+|को\\s+)?([\\p{L}\\p{M} ]{2,20}?)\\s+(?:naam se|नाम से)\\s+(?:yaad rakho|save karo|याद रखो|सेव करो)[।.!]*$", RegexOption.IGNORE_CASE),
        Regex("^(?:seema[, ]+)?(?:ye|yeh|ये|यह)\\s+(?:jagah|जगह)\\s+(?:meri|mera|मेरी|मेरा)\\s+([\\p{L}\\p{M}]{2,20})\\s+(?:hai|है)[।.!]*$", RegexOption.IGNORE_CASE),
        Regex("^(?:seema[, ]+)?(?:এই|এ)\\s+(?:জায়গাটা|জায়গাটাকে|জায়গা)\\s+([\\p{L}\\p{M} ]{2,20}?)\\s+(?:নামে)\\s+(?:মনে রাখো|সেভ করো)[।.!]*$"),
    )

    fun saveRequest(text: String): String? {
        val t = text.trim()
        for (r in SAVE) r.matchEntire(t)?.let { return it.groupValues[1].trim().takeIf { n -> n.length >= 2 } }
        return null
    }

    /** Arriving within this many metres counts as being there. */
    const val RADIUS_M = 150f

    fun saved(name: String) = "ठीक है, यह जगह \"$name\" नाम से याद रख ली। यहाँ पहुँचते ही आज के बाकी काम बता दूँगी।"

    /** Said on arrival: the place, and today's reminders still ahead (at most three). */
    fun arrival(name: String, reminders: List<Reminder>, nowMillis: Long, zone: ZoneId): String {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val end = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val ahead = reminders.filter { it.atMillis in nowMillis until end }.sortedBy { it.atMillis }.take(3)
        val tasks = if (ahead.isEmpty()) "आज और कोई काम बाकी नहीं।" else "आज बाकी काम: " + ahead.joinToString("; ") { r ->
            val t = Instant.ofEpochMilli(r.atMillis).atZone(zone)
            "${t.hour}:${t.minute.toString().padStart(2, '0')} — ${r.text}"
        } + "।"
        return "$name पहुँच गए। $tasks"
    }
}
