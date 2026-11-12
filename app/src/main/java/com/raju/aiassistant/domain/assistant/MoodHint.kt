package com.raju.aiassistant.domain.assistant

/**
 * The user's mood as their own words show it ("bahut thak gaya hoon", "tension ho rahi hai"). Only the
 * words are read — not the voice tone or the face — and only for this one answer: it adds a short line
 * to the instructions so Seema answers gently, briefly or happily. Nothing is stored.
 */
enum class Mood(val instruction: String) {
    TIRED("The user says they are tired: answer very briefly and gently; you may suggest rest if it fits."),
    STRESSED("The user sounds worried or stressed: be calm and reassuring, give one clear next step, no long lists."),
    SAD("The user sounds sad or hurt: be warm and kind first, then help; never joke about it."),
    ANGRY("The user sounds annoyed or angry: stay calm, do not argue, apologise briefly if Seema made a mistake, fix it."),
    HAPPY("The user sounds happy: share the joy in one short line, then answer."),
}

private fun words(vararg w: String) = Regex("(?<![\\p{L}\\p{M}])(?:${w.joinToString("|")})(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE)

private val TIRED = words("thak gaya", "thak gayi", "thaka hua", "thaki hui", "thakan", "thakaan", "neend aa rahi", "so jaana hai", "थक गया", "थक गई", "थकान", "नींद आ रही", "tired", "ক্লান্ত")
private val STRESSED = words("tension", "tenshan", "pareshan", "pareshaan", "chinta", "dar lag raha", "ghabrahat", "stress", "टेंशन", "परेशान", "चिंता", "डर लग रहा", "घबराहट", "চিন্তা", "টেনশন")
private val SAD = words("dukhi", "udaas", "udas", "bura lag raha", "rona aa raha", "akela", "दुखी", "उदास", "बुरा लग रहा", "रोना आ रहा", "अकेला", "sad", "মন খারাপ")
private val ANGRY = words("gussa", "gussa aa raha", "pagal ho", "bakwas", "bekar ho", "kuch kaam ki nahi", "गुस्सा", "बकवास", "बेकार हो", "রাগ")
private val HAPPY = words("bahut khush", "khush hoon", "khushi", "maza aa gaya", "mazaa aa gaya", "badhai", "बहुत खुश", "खुश हूँ", "खुशी", "मज़ा आ गया", "मजा आ गया", "খুশি")

/** The strongest mood in the words; null when none shows (most sentences). */
fun moodOf(text: String): Mood? = when {
    ANGRY.containsMatchIn(text) -> Mood.ANGRY
    SAD.containsMatchIn(text) -> Mood.SAD
    STRESSED.containsMatchIn(text) -> Mood.STRESSED
    TIRED.containsMatchIn(text) -> Mood.TIRED
    HAPPY.containsMatchIn(text) -> Mood.HAPPY
    else -> null
}
