package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.ai.ProviderId

/** Which brain a message needs: a quick answer, or careful thinking. */
enum class BrainTier { FAST, DEEP }

private val DEEP_WORDS = Regex(
    "\\b(pnr|train|ticket|tickets|itinerary|journey|yatra|kharcha|kharche|expense|expenses|hisaab|hisab|khata|summary|analy[sz]e|analysis|" +
        "compare|comparison|plan|planning|steps?|strategy|explain|samjhao|samjha do|why|kyon|kyun|calculate|report|code|program|letter|" +
        "essay|draft|research|translate|detail|detailed|vistar)\\b|" +
        "योजना|समझाओ|समझा दो|क्यों|हिसाब|खर्च|टिकट|ट्रेन|यात्रा|रिपोर्ट|तुलना|विस्तार|पत्र|हিসাব|খরচ|টিকিট|ট্রেন|কেন|বুঝিয়ে",
    RegexOption.IGNORE_CASE,
)

/**
 * Sorts a message on the phone, before any AI request: long, multi-part or reasoning-type messages
 * (PNR / train, expenses, plans, comparisons, explanations, letters, code) need the DEEP brain;
 * greetings, small talk and quick questions get the FAST one.
 */
fun brainTier(text: String): BrainTier {
    val t = text.trim()
    val words = t.split(Regex("\\s+")).count { it.isNotBlank() }
    return when {
        t.length > 160 || words > 28 -> BrainTier.DEEP
        t.count { it == '?' } >= 2 -> BrainTier.DEEP
        DEEP_WORDS.containsMatchIn(t) -> BrainTier.DEEP
        else -> BrainTier.FAST
    }
}

/** Groq answers quick messages fastest; Gemini handles careful thinking. */
fun preferredProvider(tier: BrainTier): ProviderId = if (tier == BrainTier.FAST) ProviderId.GROQ else ProviderId.GEMINI

/** Recent exchanges (user + reply) sent with a request: a quick answer needs less of the conversation. */
fun historyExchangesFor(tier: BrainTier, configured: Int): Int = if (tier == BrainTier.FAST) minOf(2, configured) else configured

/** Saved memories sent with a request: the 2 most relevant for a quick answer, up to 12 otherwise. */
fun memoryLimitFor(tier: BrainTier): Int = if (tier == BrainTier.FAST) 2 else 12
