package com.raju.aiassistant.domain.notify

/**
 * What to say for an incoming SIM call. The caller's name is used only when it was found in the
 * user's Contacts; the number is spoken only if the user enabled that; otherwise no identity is
 * invented ("unknown number" / "ek call").
 */
fun callAnnouncementText(contactName: String?, number: String?, speakNumber: Boolean, nickname: String? = null): String {
    val digits = number?.filter { it.isDigit() || it == '+' }.orEmpty()
    val prefix = nickPrefix(nickname)
    return when {
        !contactName.isNullOrBlank() -> "$prefix$contactName ka call aa raha hai."
        digits.length >= 3 && speakNumber -> "${prefix}Number ${digits.toList().joinToString(" ")} se call aa raha hai."
        digits.isEmpty() -> "${prefix}Ek call aa raha hai. Number chhupa hua hai."
        else -> "${prefix}Unknown number ka call aa raha hai."
    }
}

/** One announcement per call: the same number ringing again within [windowMs] is ignored. */
class CallDeduper(private val windowMs: Long = 60_000) {
    private val recent = HashMap<String, Long>()

    @Synchronized
    fun shouldAnnounce(number: String?, now: Long): Boolean {
        val key = number?.filter { it.isDigit() }?.takeLast(10).orEmpty()
        if (key.isEmpty()) return true // hidden number: every ring is announced
        recent.entries.removeAll { now - it.value > windowMs }
        if (recent.containsKey(key)) return false
        recent[key] = now
        return true
    }
}

/** What the user said about a ringing call. */
enum class CallCommand { ANSWER, REJECT }

// "No" words first: "mat uthao" / "nahi uthana" must never answer the call.
private val REJECT_WORDS = Regex(
    "\\b(mat|nahi|nahin|no|nope|kaat|kaato|kaat do|kat do|cut|reject|decline|band karo|baad me|baad mein)\\b|मत|नहीं|नही|काट|रिजेक्ट|बाद में|কেটে|কাটো|পরে",
    RegexOption.IGNORE_CASE,
)
private val ANSWER_WORDS = Regex(
    "\\b(haan|han|ha|haa|haanji|ji|yes|ok|okay|utha|uthao|utha lo|uthalo|uthaa|pick|pick up|receive|answer|accept|baat karao)\\b|हाँ|हां|उठा|रिसीव|ठीक है|হ্যাঁ|ধরো|রিসিভ",
    RegexOption.IGNORE_CASE,
)

/** "Haan, utha lo" → ANSWER; "nahi, kaat do" / "mat uthao" → REJECT; anything else → null. */
fun callCommand(heard: String): CallCommand? {
    val t = heard.trim()
    if (t.isEmpty()) return null
    return when {
        REJECT_WORDS.containsMatchIn(t) -> CallCommand.REJECT
        ANSWER_WORDS.containsMatchIn(t) -> CallCommand.ANSWER
        else -> null
    }
}

/** What Seema asks after naming the caller, so "haan" / "nahi" can be answered by voice. */
const val ASK_TO_ANSWER = "Uthau ya cut karu?"
