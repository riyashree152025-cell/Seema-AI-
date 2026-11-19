package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.language.SeemaLanguage

/**
 * Her own fixed lines ("हो गया, मैसेज भेज दिया") used to end in the user's name every time. She knows
 * the name but does not keep saying it: most lines carry nothing, and only now and then a soft pet name
 * in his language (Hindi जान / सोना …, Bengali সোনা / বাবু …) — never forced, never the same twice in a row.
 */
class Endearment {
    private var count = 0
    private var last = ""

    /** The pet name for the next line, or "" (three lines in four carry none). */
    fun next(language: SeemaLanguage): String {
        val words = petNames(language)
        count++
        if (words.isEmpty() || count % EVERY != 1) return ""
        var w = words[(count / EVERY) % words.size]
        if (w == last) w = words[(count / EVERY + 1) % words.size]
        last = w
        return w
    }

    /** [line] with the user's name replaced by the next pet name, or simply left out. */
    fun personalize(line: String, language: SeemaLanguage): String =
        if (!NAME.containsMatchIn(line)) line else replaceName(line, next(language))

    companion object {
        private val NAME = Regex("\\s*(?:राजू|রাজু)(?:\\s*(?:जी|भाई|ভাই))?")

        /** A pet name rides on one of her fixed lines in this many. */
        const val EVERY = 4

        fun petNames(language: SeemaLanguage): List<String> =
            if (language == SeemaLanguage.BENGALI) listOf("সোনা", "বাবু", "জান") else listOf("जान", "सोना", "बाबू")

        /**
         * Puts [word] where the name was ("हाँ राजू, …" → "हाँ बाबू, …"), or leaves the name out and tidies
         * the sentence ("राजू, यूट्यूब पर …" → "यूट्यूब पर …", "रोक दिया है राजू।" → "रोक दिया है।").
         */
        fun replaceName(line: String, word: String): String {
            val out = NAME.replace(line, if (word.isEmpty()) "" else " $word")
            return out.trimStart(' ', ',', '،').replace(Regex("\\s+([,।!?.])"), "$1").replace(Regex(",\\s*([।!?.])"), "$1").trim()
        }
    }
}
