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

/**
 * The announcement when the phone rings but Seema has no caller details (she is not the call
 * screening app, so Android does not tell her the number): no name or "hidden number" is invented.
 */
fun ringingAnnouncementText(nickname: String? = null): String = "${nickPrefix(nickname)}Ek call aa raha hai."

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
    "\\b(mat|nahi|nahin|no|nope|kaat|kaato|kaat do|kat do|kat|kato|cut|reject|decline|disconnect|hata do|band karo|baad me|baad mein)\\b|मत|नहीं|नही|काट|(?<![\\u0900-\\u097F])कट(?![\\u0900-\\u097F])|रिजेक्ट|डिस्कनेक्ट|बंद कर|बाद में|কেটে|কাটো|পরে",
    RegexOption.IGNORE_CASE,
)
private val ANSWER_WORDS = Regex(
    "\\b(haan|han|ha|haa|haanji|ji|yes|ok|okay|utha|uthao|utha lo|uthalo|uthaa|pick|pick up|receive|recieve|risiv|answer|accept|baat karao)\\b|हाँ|हां|उठा|रिसीव|रिसिव|रीसीव|(?<![\\u0900-\\u097F])पिक(?![\\u0900-\\u097F])|एक्सेप्ट|आंसर|ठीक है|হ্যাঁ|ধরো|রিসিভ",
    RegexOption.IGNORE_CASE,
)

private val SPEAKER = Regex("\\b(speaker|spiker|speakerphone|loudspeaker|loud)\\b|स्पीकर|स्पिकर|लाउडस्पीकर|স্পিকার", RegexOption.IGNORE_CASE)

/** True when the user wants the call on the loudspeaker ("receive karo aur speaker mein daalo"). */
fun wantsSpeaker(heard: String): Boolean = SPEAKER.containsMatchIn(heard)

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
const val ASK_TO_ANSWER = "Uthau ya kaatu?"

/** Said instead of the question when the caller already hung up while Seema was announcing. */
fun endedCallText(contactName: String?): String =
    if (contactName.isNullOrBlank()) "Call aaya tha, ab kat chuka hai." else "$contactName ka call aaya tha, ab kat chuka hai."

// "Bolo" counts only with words after it ("usko bolo main busy hoon"); "haan bolo" alone stays an answer.
private const val TELL = "(?:bol do|bol de|bolo|bolna|keh do|kah do|keh de|kaho|likh do|bata do|bata dena|message kar do|msg kar do|sms kar do|message bhej do|reply kar do|बोल दो|बोलो|बोलना|कह दो|कहो|लिख दो|बता दो|बता देना|मैसेज कर दो|मैसेज भेज दो|বলে দাও|লিখে দাও)"
private val TELL_FIRST = Regex("^(?:(?:usko|use|unko|unhe|उसको|उसे|उन्हें|ওকে) )?$TELL (?:ki |कि |যে )?(.{2,140})$", RegexOption.IGNORE_CASE)
private val TELL_LAST = Regex("^(?:(?:usko|use|unko|unhe|उसको|उसे|उन्हें|ওকে) )?(.{2,140}?) $TELL$", RegexOption.IGNORE_CASE)
/** "Theek hai", "accha", "haan", "ok" before the real words (repeated: "haan theek hai …"). */
private val OPENERS = Regex(
    "^(?:(?:theek hai|thik hai|theek h|accha|acha|achha|haan|han|ha|ok|okay|ji|haan ji|ठीक है|अच्छा|हाँ|हां|ओके|জি|ঠিক আছে)[,\\s]+)+",
    RegexOption.IGNORE_CASE,
)
/** "… usko bol do (ki) busy hoon": the reply is what follows "bol do" (an SMS body must be words, not empty). */
private val TELL_ANYWHERE = Regex("(?:^|\\s)(?:(?:usko|use|unko|unhe|उसको|उसे|उन्हें|ওকে) )?$TELL (?:ki |कि |যে )?(.{2,140})$", RegexOption.IGNORE_CASE)

/** Words that are a reply on their own: "10 minute mein call karta hoon", "meeting mein hoon". */
private val SELF_REPLY = Regex(
    "call karta hoon|call karti hoon|call karunga|call karungi|call back|busy hoon|busy hu|meeting mein hoon|gaadi chala raha|drive kar raha|" +
        "baad mein call|baad me call|baad mein baat|baad me baat|thodi der mein|thodi der me|meeting mein|meeting me|class mein hoon|" +
        "बाद में कॉल|बाद में बात|थोड़ी देर में|थोड़ी देर बाद|मीटिंग में|क्लास में हूं|क्लास में हूँ|" +
        "कॉल करता हूँ|कॉल करता हूं|कॉल करती हूँ|कॉल करती हूं|कॉल करूँगा|कॉल करूंगा|बिज़ी हूँ|बिज़ी हूं|बिजी हूँ|बिजी हूं|व्यस्त हूँ|व्यस्त हूं|मीटिंग में हूँ|मीटिंग में हूं|পরে কল করছি|ব্যস্ত আছি",
    RegexOption.IGNORE_CASE,
)

/**
 * While a call rings: "bol do abhi busy hoon" / "10 minute mein call karta hoon" → the SMS text
 * ("Abhi busy hoon."), to send after cutting the call; null when the words are not such a reply.
 * The user's own words are sent, only tidied (first letter, full stop).
 */
fun callReplyText(heard: String): String? {
    val t = heard.trim().trimEnd('.', '!', '।', ' ').replace(Regex("\\s+"), " ")
    if (t.isEmpty()) return null
    // "theek hai usko bol do busy hoon …": conversational openers first, then "bol do" anywhere.
    val core = t.replace(OPENERS, "").trim()
    val words = TELL_FIRST.matchEntire(core)?.groupValues?.get(1)
        ?: TELL_ANYWHERE.find(core)?.groupValues?.get(1)
        ?: TELL_LAST.matchEntire(core)?.groupValues?.get(1)
        ?: core.takeIf { SELF_REPLY.containsMatchIn(it) }
        ?: return null
    val body = words.trim().removePrefix("ki ").removePrefix("कि ").trim()
    if (body.length < 2) return null
    val first = body.first()
    val tidy = (if (first.isLowerCase()) first.uppercaseChar() + body.substring(1) else body)
    return if (tidy.last() in ".!?।") tidy else if (tidy.any { it in 'ऀ'..'ॿ' || it in 'ঀ'..'৿' }) "$tidy।" else "$tidy."
}
