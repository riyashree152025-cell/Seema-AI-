package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.language.SeemaLanguage
import com.raju.aiassistant.domain.model.Personality

/**
 * Her own fixed lines ("हो गया, मैसेज भेज दिया") used to end in the user's name every time. She knows
 * the name but does not keep saying it: most lines now carry nothing, and now and then a pet name that
 * fits her personality and language — Hindi बाबू / सोना / जान …, Bengali সোনা / বাবু / পাগলা …, a
 * friend's भिड़ू / ভাই in Tapori — never the same one twice in a row. Pro stays plain.
 */
class Endearment {
    private var count = 0
    private var last = ""

    /** The pet name for the next line, or "" (two lines in three carry none). */
    fun next(personality: Personality, language: SeemaLanguage): String {
        val words = petNames(personality, language)
        count++
        if (words.isEmpty() || count % 3 != 1) return ""
        var w = words[(count / 3) % words.size]
        if (w == last) w = words[(count / 3 + 1) % words.size]
        last = w
        return w
    }

    /** [line] with the user's name replaced by the next pet name, or simply left out. */
    fun personalize(line: String, personality: Personality, language: SeemaLanguage): String =
        if (!NAME.containsMatchIn(line)) line else replaceName(line, next(personality, language))

    companion object {
        private val NAME = Regex("\\s*(?:राजू|রাজু)(?:\\s*(?:जी|भाई|ভাই))?")

        fun petNames(personality: Personality, language: SeemaLanguage): List<String> {
            val bengali = language == SeemaLanguage.BENGALI
            return when (personality) {
                Personality.COMPANION -> if (bengali) listOf("সোনা", "বাবু", "জান", "লক্ষ্মীটি", "সোনামণি") else listOf("बाबू", "सोना", "जान", "जानू", "बेबी")
                Personality.TAPORI -> if (bengali) listOf("ভাই", "দোস্ত") else listOf("भिड़ू", "बॉस", "यार")
                Personality.MASTI, Personality.DOST -> if (bengali) listOf("দোস্ত") else listOf("यार", "दोस्त")
                Personality.PRO -> emptyList()
            }
        }

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
