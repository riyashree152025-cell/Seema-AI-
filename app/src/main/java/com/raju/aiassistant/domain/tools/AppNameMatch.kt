package com.raju.aiassistant.domain.tools

/**
 * Which installed app a spoken name means. Speech recognition is Hindi now, so app names often arrive in
 * Devanagari ("क्रोम", "यूट्यूब", "व्हाट्सएप") while the phone lists them in English: the same sound-skeleton
 * as contact names is compared, plus a few Hindi words for system apps ("घड़ी" = Clock).
 */
object AppNameMatch {
    private val HINDI_WORDS = mapOf(
        "घड़ी" to "clock", "घडी" to "clock", "मैसेज" to "messages", "संदेश" to "messages", "फ़ोन" to "phone", "फोन" to "phone",
        "संपर्क" to "contacts", "कॉन्टैक्ट" to "contacts", "फ़ाइल" to "files", "फाइल" to "files", "गाना" to "music",
    )

    /** The best label among [labels] for [spoken], or null. Exact, prefix and contains first, then by sound. */
    fun best(spoken: String, labels: List<String>): String? {
        val w = (HINDI_WORDS[spoken.trim()] ?: spoken).trim().lowercase()
        if (w.isEmpty()) return null
        labels.firstOrNull { it.lowercase() == w }?.let { return it }
        labels.firstOrNull { it.lowercase().startsWith(w) }?.let { return it }
        labels.firstOrNull { it.lowercase().contains(w) }?.let { return it }
        val sk = ContactMatch.skeleton(w)
        if (sk.length < 2) return null
        labels.firstOrNull { ContactMatch.skeleton(it) == sk }?.let { return it }
        if (sk.length >= 3) labels.firstOrNull { ContactMatch.skeleton(it).startsWith(sk) }?.let { return it }
        return null
    }
}
