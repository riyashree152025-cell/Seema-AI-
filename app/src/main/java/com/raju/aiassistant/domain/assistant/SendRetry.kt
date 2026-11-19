package com.raju.aiassistant.domain.assistant

/**
 * "Tum dabao", "Send kar do", "তোমাকে চাপতে হবে, পাঠিয়ে দিতে হবে": said right after Seema reported that WhatsApp's
 * Send could not be pressed, it means "press it again" — not a new message to someone.
 */
object SendRetry {
    private val PRESS = Regex(
        "(?<![\\p{L}\\p{M}])(?:daba|dabao|dabado|dabaa|press|click|दबा|दबाओ|दबाइए|दबाना|दबाओ|चापो|চাপ|চাপো|চাপুন|চাপতে|চেপে|টিপ|টিপো|টিপে|টেপ)",
        RegexOption.IGNORE_CASE,
    )
    private val SEND = Regex(
        "(?<![\\p{L}\\p{M}])(?:send|bhej|bhejo|bhejdo|भेज|भेजो|भेजिए|भेजना|পাঠা|পাঠাও|পাঠিয়ে|পাঠিয়ে|পাঠান|ভেজ|ভেজো)",
        RegexOption.IGNORE_CASE,
    )
    /** A question about whether it went ("bheja kya?") is not a request to press again. */
    private val QUESTION = Regex("(?:kya|क्या|কি|কিনা|কী)\\s*[?？]?$|[?？]$", RegexOption.IGNORE_CASE)

    const val MAX_CHARS = 80

    fun asksToPress(text: String): Boolean {
        val t = text.trim().trimEnd('.', '!', '।')
        if (t.isEmpty() || t.length > MAX_CHARS || QUESTION.containsMatchIn(t)) return false
        return PRESS.containsMatchIn(t) || SEND.containsMatchIn(t)
    }

    private val SELF = Regex(
        "^(?:self|me|myself|mujhe|mujh|mera|meri|main|मुझे|मैं|मेरा|मेरी|खुद|আমাকে|আমি|আমার|নিজে|নিজের|user|raju|राजू|রাজু)?$",
        RegexOption.IGNORE_CASE,
    )

    /** "WhatsApp khol ke dikhao bheja ki nahi": the AI names the user ("self", "me") instead of the chat. */
    fun meansNoChat(contact: String): Boolean = SELF.matches(contact.trim())
}
