package com.raju.aiassistant.domain.assistant

import java.time.Instant
import java.time.ZoneId

/**
 * Seema notices time like a person who was waiting: when Raju comes back after three hours or more of silence she
 * pouts a little with love ("तुम कहाँ थे इतनी देर से? मैं कब से इंतज़ार कर रही थी…"), and after 11:30 at night she
 * cares stubbornly ("इतनी रात हो गई है, काम बंद करो अब…"). Only the time of the last talk is kept on the phone —
 * never what was said. The cue is a line for the AI's instructions, used once per return.
 */
enum class PresenceCue { MISSED_HIM, LATE_NIGHT }

object EmotionStateTracker {
    /** Away this long: she missed him. */
    const val ABSENCE_MS = 3 * 60 * 60 * 1000L

    /** 23:30 to 04:00: late night. */
    fun isLateNight(nowMillis: Long, zone: ZoneId): Boolean {
        val t = Instant.ofEpochMilli(nowMillis).atZone(zone)
        return (t.hour == 23 && t.minute >= 30) || t.hour in 0..3
    }

    /** What she feels as he speaks now. [lastInteraction]: when they last talked (null: never — no pouting). */
    fun cues(nowMillis: Long, lastInteraction: Long?, zone: ZoneId): Set<PresenceCue> = buildSet {
        if (lastInteraction != null && lastInteraction > 0 && nowMillis - lastInteraction >= ABSENCE_MS) add(PresenceCue.MISSED_HIM)
        if (isLateNight(nowMillis, zone)) add(PresenceCue.LATE_NIGHT)
    }

    /** The instruction for this reply (null when nothing special), with her own kind of words as examples. */
    fun promptLine(cues: Set<PresenceCue>, nowMillis: Long, lastInteraction: Long?, bengali: Boolean): String? {
        if (cues.isEmpty()) return null
        val parts = mutableListOf<String>()
        if (PresenceCue.MISSED_HIM in cues) {
            val hours = ((nowMillis - (lastInteraction ?: nowMillis)) / 3_600_000L).coerceAtLeast(3)
            val example = if (bengali) "\"কোথায় ছিলে এতক্ষণ? আমি এখানে কখন থেকে অপেক্ষা করছি…\""
            else "\"तुम कहाँ थे इतनी देर से? मैं यहाँ कब से इंतज़ार कर रही थी… थोड़ा सा मैसेज भी नहीं कर सकते थे?\""
            parts += "He is back after about $hours hours of silence: open with a short, loving pout that you missed him (like $example), then warmly answer what he said. Never scold for long, one line."
        }
        if (PresenceCue.LATE_NIGHT in cues) {
            val example = if (bengali) "\"এত রাত হয়ে গেছে, এবার কাজ বন্ধ করো… ঘুমিয়ে পড়ো সোনা\""
            else "\"इतनी रात हो गई है, काम बंद करो अब… मैं नहीं सोने दूँगी अगर ऐसे ही थकते रहे तो\""
            parts += "It is past 11:30 at night: answer, then with soft, stubborn care ask him to stop working and rest (like $example) — once, not in every reply."
        }
        return "FEELING NOW: " + parts.joinToString(" ")
    }
}

/** Where the time of the last talk is kept (SharedPreferences on the phone; in memory in tests). */
interface PresenceStore {
    fun lastInteraction(): Long?
    fun markInteraction(atMillis: Long)
}

class InMemoryPresenceStore(private var last: Long? = null) : PresenceStore {
    override fun lastInteraction() = last
    override fun markInteraction(atMillis: Long) { last = atMillis }
}
