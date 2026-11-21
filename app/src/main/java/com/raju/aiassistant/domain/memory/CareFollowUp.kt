package com.raju.aiassistant.domain.memory

/**
 * "Sir dard ho raha hai", "bukhar hai", "bahut tension hai", "শরীর খারাপ": Seema remembers what was wrong (only the
 * kind of trouble and when, on the phone) and asks about it the next time they talk a few hours later —
 * "सिर दर्द अब कैसा है?" — once, like someone who cares. Also a small mood diary: how often he was tired or worried.
 */
object CareFollowUp {
    private fun words(vararg w: String) = Regex("(?<![\\p{L}\\p{M}])(?:${w.joinToString("|")})(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE)

    /** What was wrong → how she names it when asking later. */
    private val CONCERNS: List<Pair<Regex, String>> = listOf(
        words("sir dard", "sar dard", "sir me dard", "सिर दर्द", "सिर में दर्द", "सर दर्द", "মাথা ব্যথা", "headache") to "सिर दर्द",
        words("bukhar", "bukhaar", "fever", "बुखार", "জ্বর") to "बुखार",
        words("pet dard", "pet kharab", "पेट दर्द", "पेट खराब", "পেট ব্যথা", "পেট খারাপ") to "पेट",
        words("kamar dard", "back pain", "कमर दर्द", "কোমর ব্যথা") to "कमर दर्द",
        words("sardi", "zukam", "khansi", "सर्दी", "ज़ुकाम", "जुकाम", "खांसी", "खाँसी", "সর্দি", "কাশি") to "सर्दी-खाँसी",
        words("tabiyat kharab", "tabiyat theek nahi", "tabiyat thik nahi", "तबियत खराब", "तबीयत खराब", "तबियत ठीक नहीं", "শরীর খারাপ") to "तबियत",
        words("tension", "pareshan", "pareshaan", "chinta", "टेंशन", "परेशान", "चिंता", "চিন্তা", "টেনশন") to "टेंशन",
        words("thak gaya", "bahut thaka", "thakan", "थक गया", "बहुत थका", "थकान", "ক্লান্ত") to "थकान",
    )

    /** The trouble in [text], or null. Questions about others ("Rahul ko bukhar hai?") still count as his worry. */
    fun concernOf(text: String): String? = CONCERNS.firstOrNull { (r, _) -> r.containsMatchIn(text) }?.second

    /** Ask after this long (and not after a week: by then it is old news). */
    const val ASK_AFTER_MS = 4 * 60 * 60 * 1000L
    const val FORGET_AFTER_MS = 7 * 24 * 60 * 60 * 1000L

    /** The line for her instructions when it is time to ask about [concern] (said at [saidAt]), or null. */
    fun promptLine(concern: String, saidAt: Long, now: Long, bengali: Boolean): String? {
        val age = now - saidAt
        if (age < ASK_AFTER_MS || age > FORGET_AFTER_MS) return null
        val example = if (bengali) "\"এখন কেমন আছো? শরীরটা ঠিক আছে তো?\"" else "\"$concern अब कैसा है? ठीक लग रहा है ना?\""
        return "CARE: earlier he mentioned $concern. Before or after answering, softly ask how he is now (like $example) — once, briefly."
    }
}
