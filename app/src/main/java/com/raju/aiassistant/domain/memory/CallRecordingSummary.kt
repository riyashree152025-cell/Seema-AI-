package com.raju.aiassistant.domain.memory

/**
 * "Aakhri call recording ka saar batao", "last call me kya baat hui", "শেষ কলের রেকর্ডিং-এ কী কথা হল": the phone's own
 * newest call recording (the phone records it, not Seema) is heard by Gemini only on this request; she says the gist in
 * two or three sentences, and each promise in it becomes one of her reminders, as when he tells her after a call.
 */
object CallRecordingSummary {
    private val ASK = Regex(
        "^(?:seema[, ]+)?(?:(?:aakhri|akhri|last|pichli|pichhli|आख़िरी|आखिरी|पिछली|শেষ|শেষের)\\s+)?(?:call|kol|कॉल|কল|কলের)\\s*(?:ki|ka|की|का|এর)?\\s*" +
            "(?:recording|rikording|रिकॉर्डिंग|রেকর্ডিং)(?:\\s*-?\\s*এ)?\\s*(?:ka|ki|का|की)?\\s*(?:saar|summary|सार|समरी|सारांश|সারাংশ)?\\s*" +
            "(?:batao|bata do|sunao|kya baat hui|me kya baat hui|बताओ|सुनाओ|में क्या बात हुई|क्या बात हुई|বলো|কী কথা হল|কি কথা হল)?[?।.]*$",
        RegexOption.IGNORE_CASE,
    )

    fun asks(text: String): Boolean {
        val t = text.trim()
        return t.length <= 80 && ASK.matches(t) && Regex("recording|रिकॉर्डिंग|রেকর্ডিং|rikording", RegexOption.IGNORE_CASE).containsMatchIn(t)
    }

    fun prompt(bengali: Boolean): String =
        "This is a phone call recording of Raju (a travel agent) with someone. " +
            (if (bengali) "In Bengali (Bengali script)" else "In Hindi (Devanagari)") +
            ", give the gist in 2-3 short spoken sentences: who called (if said), what was discussed, what was decided. " +
            "Then, on separate lines, each promise made, in simple Hinglish starting with PROMISE: — e.g. \"PROMISE: Rahul kal 5000 rupaye dega\", " +
            "\"PROMISE: shaam ko Amit ko call karna hai\". No promises → no PROMISE lines. Never invent anything not said."

    /** The spoken gist (without the PROMISE lines). */
    fun gist(reply: String): String = reply.lineSequence().filterNot { it.trim().startsWith("PROMISE:", ignoreCase = true) }.joinToString(" ").replace(Regex("\\s+"), " ").trim()

    /** The promise sentences, ready for [CallMemoryExtractor]. */
    fun promises(reply: String): List<String> = reply.lineSequence().map { it.trim() }
        .filter { it.startsWith("PROMISE:", ignoreCase = true) }.map { it.substringAfter(':').trim() }.filter { it.isNotEmpty() }.toList()

    /** Larger than this is not sent (the request would be too big). */
    const val MAX_BYTES = 15 * 1024 * 1024
}
