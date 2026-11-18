package com.raju.aiassistant.domain.assistant

/**
 * What the user said in the 2.5 s after "Riya mil gayi, ab call laga rahi hoon": stop it, or the right name.
 * "ruko" / "nahi" / "galat" / "cancel" → [Cancel]; "Siya nahi Riya", "ruko, nahi Riya ko", "galat, Rizwan ko
 * bhejo" → [Rename]; anything else (silence, "haan", noise) → null: go ahead.
 */
sealed interface Correction {
    data object Cancel : Correction
    data class Rename(val name: String) : Correction

    companion object {
        private val CANCEL = Regex(
            "(?<![\\p{L}\\p{M}])(?:ruko|ruk jao|ruk|rukho|nahi|nahin|nai|mat|galat|cancel|stop|band karo|wait|रुको|रुक जाओ|रुक|नहीं|नही|मत|गलत|कैंसल|स्टॉप|থামো|না|ভুল)(?![\\p{L}\\p{M}])",
            RegexOption.IGNORE_CASE,
        )
        // "nahi Riya", "galat, Riya", "balki Riya": the right name comes after one of these.
        private val THEN_NAME = Regex(
            "(?<![\\p{L}\\p{M}])(?:nahi|nahin|nai|नहीं|नही|না|galat|गलत|ভুল|balki|बल्कि)[,\\s]+(.+)$",
            RegexOption.IGNORE_CASE,
        )
        private val TRAILING = Regex(
            "(?:\\s+(?:ko|को|কে|ka|का|ki|की))?(?:\\s+(?:call|kol|कॉल|फ़ोन|फोन|phone|message|मैसेज|msg|whatsapp|व्हाट्सएप|bhejo|bhej|भेजो|भेज|karo|kar|करो|कर|lagao|लगाओ|do|दो|de|दे|dena|देना|hai|है))*$",
            RegexOption.IGNORE_CASE,
        )
        private val NOT_A_NAME = Regex(
            "^(?:nahi|nahin|nai|ruko|mat|galat|cancel|stop|haan|ha|ji|ok|wait|नहीं|रुको|मत|गलत|हाँ|हां|जी|না|হ্যাঁ)$",
            RegexOption.IGNORE_CASE,
        )

        fun parse(heard: String): Correction? {
            val t = heard.trim().trimEnd('.', '!', '?', '।').replace(Regex("\\s+"), " ")
            if (t.isEmpty()) return null
            // The last "nahi X": "Siya nahi Riya" / "ruko ruko, nahi nahi, Riya".
            THEN_NAME.findAll(t).lastOrNull()?.let { m ->
                var rest = m.groupValues[1].trim().trim(',', ' ')
                rest = rest.replace(TRAILING, "").trim().trim(',', ' ')
                val words = rest.split(Regex("[\\s,]+")).filter { it.isNotBlank() && !NOT_A_NAME.matches(it) }
                if (words.isNotEmpty() && words.size <= 3 && words.joinToString("").any { it.isLetter() }) {
                    return Rename(words.joinToString(" "))
                }
            }
            return if (CANCEL.containsMatchIn(t)) Cancel else null
        }
    }
}
