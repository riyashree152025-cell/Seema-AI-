package com.raju.aiassistant.domain.language

/**
 * Words the phone keeps mishearing, taught once by Raju: "jab main mitthu bolun to Mitu samajhna", "मिट्ठू का मतलब Mitu है",
 * "মিঠু মানে Mitu". Kept in Seema's memory as one line ("शब्द सुधार: mitthu → Mitu") and applied to everything she hears.
 */
object SpeechCorrections {
    private val TEACH = listOf(
        Regex("^(?:seema[, ]+)?(?:jab|जब)\\s+(?:main|mai|मैं)\\s+[\"'“]?(.{2,30}?)[\"'”]?\\s+(?:bolun|bolu|boloon|बोलूँ|बोलूं|बोलू)\\s+(?:to|तो)\\s+[\"'“]?(.{2,30}?)[\"'”]?\\s+(?:samajhna|samjhna|समझना|सुनना)[।.!]*$", RegexOption.IGNORE_CASE),
        Regex("^(?:seema[, ]+)?[\"'“]?(.{2,30}?)[\"'”]?\\s+(?:ka matlab|का मतलब|মানে)\\s+[\"'“]?(.{2,30}?)[\"'”]?\\s*(?:hai|है)?[।.!]*$", RegexOption.IGNORE_CASE),
    )

    /** (heard, meant) when [text] teaches a correction. */
    fun teach(text: String): Pair<String, String>? {
        for (r in TEACH) r.matchEntire(text.trim())?.let { m ->
            val heard = m.groupValues[1].trim()
            val meant = m.groupValues[2].trim()
            val question = Regex("^(?:kya|क्या|কী|কি|what)$", RegexOption.IGNORE_CASE)
            if (heard.isNotEmpty() && meant.isNotEmpty() && !heard.equals(meant, true) && !question.matches(meant) &&
                !Regex("^(?:iska|uska|is|us|इसका|उसका|এর)$", RegexOption.IGNORE_CASE).matches(heard)
            ) return heard to meant
        }
        return null
    }

    private const val PREFIX = "शब्द सुधार: "
    fun factText(heard: String, meant: String) = "$PREFIX$heard → $meant"

    fun fromFact(text: String): Pair<String, String>? {
        if (!text.startsWith(PREFIX)) return null
        val parts = text.removePrefix(PREFIX).split(" → ")
        return if (parts.size == 2 && parts.all { it.isNotBlank() }) parts[0].trim() to parts[1].trim() else null
    }

    /** [text] with every taught word replaced (whole words only, any case). */
    fun apply(text: String, corrections: List<Pair<String, String>>): String = corrections.fold(text) { t, (heard, meant) ->
        Regex("(?<![\\p{L}\\p{M}])" + Regex.escape(heard) + "(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE).replace(t, Regex.escapeReplacement(meant))
    }

    fun confirmation(heard: String, meant: String) = "ठीक है, अब से \"$heard\" सुनूँगी तो \"$meant\" समझूँगी।"
}
