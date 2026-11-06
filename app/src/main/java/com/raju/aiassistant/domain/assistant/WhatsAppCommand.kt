package com.raju.aiassistant.domain.assistant

/** How the user wants the message written ("Hinglish में", "English में"), when they said so. */
enum class MessageStyle { HINGLISH, ENGLISH, HINDI }

/**
 * "WhatsApp a message", understood on the phone without asking an AI:
 * - "Rizwan Travels को WhatsApp में मैसेज भेजो कि दादा पेमेंट नहीं आया" -> to Rizwan Travels, that text;
 * - "Rizwan Travels को WhatsApp करना है" -> to Rizwan Travels, the text is asked next;
 * - "किसी को WhatsApp करना है" / "WhatsApp करना है" -> who and what are both asked.
 * Seema always reads the message back and sends only after "हाँ".
 */
data class WhatsAppRequest(val contact: String?, val message: String?, val style: MessageStyle? = null) {
    companion object {
        private const val SEP = "[\\s,.!?।]+"
        private const val WA = "(?:whats\\s?app|whatsap|watsapp|watsap|what's\\s?app|वॉट्सऐप|व्हाट्सएप|व्हाट्सऐप|वॉट्सएप|व्हाट्सअप|वाट्सएप|वॉट्सअप|व्हाट्सप|वाट्सऐप|व्हाट्स\\s?ऐप|व्हाट्स\\s?एप|वॉट्स\\s?ऐप)"
        private const val MSG = "(?:message|messages|msg|massage|messeg|मैसेज|मेसेज|मैसेज़|मेसेज़|मैसेजेस|संदेश|मसेज)"
        private const val PREP = "(?:पर|में|मे|मैं|पे|pe|par|per|mein|me|main|se|से)"
        private const val KO = "(?:को|ko|ku|कू|kO)"
        private const val SEND = "(?:भेज\\s?दो|भेज\\s?दीजिए|भेज\\s?दीजिये|भेजिए|भेजिये|भेजो|भेज\\s?दे|भेजना|भेज|कर\\s?दो|कर\\s?दीजिए|करो|कीजिए|डाल\\s?दो|डालो|लिख\\s?दो|लिखो|" +
            "bhej\\s?do|bhej\\s?dijiye|bhejiye|bhejo|bhej\\s?de|bhejna|bhej|kar\\s?do|kar\\s?dijiye|karo|kijiye|daal\\s?do|dalo|likh\\s?do|likho|send\\s?karo|send\\s?kar\\s?do|send)"
        private const val WANT = "(?:करना\\s?है|भेजना\\s?है|करना\\s?हैं|करनी\\s?है|करना|भेजना|karna\\s?hai|bhejna\\s?hai|karna|bhejna|चाहिए|chahiye)"
        private const val THAT = "(?:कि|की|ki|ke\\s?liye|के\\s?लिए|ये|यह|ye|yeh)"

        private val SMS = Regex("\\b(?:sms|एसएमएस)\\b|एसएमएस", RegexOption.IGNORE_CASE)
        private val HAS_WA_OR_MSG = Regex("$WA|$MSG", RegexOption.IGNORE_CASE)

        private val LEADING = Regex(
            "^(?:(?:seema|sima|सीमा|सिमा|hey|hi|हे|हाय|अच्छा|acha|achha|ok|okay|ओके|ठीक\\s?है|theek\\s?hai|zara|jara|ज़रा|जरा|please|प्लीज़?|सुनो|suno|अरे|arre|are|मुझे|mujhe|मेरा|ek|एक)$SEP)+",
            RegexOption.IGNORE_CASE,
        )

        /** "Rizwan Travels को (WhatsApp में) (मैसेज) भेजो कि <message>" */
        private val ONE_SHOT = Regex(
            "^(?:$WA$SEP(?:$PREP$SEP)?)?(.+?)$SEP$KO$SEP((?:$WA$SEP)?(?:$PREP$SEP)?(?:$WA$SEP)?(?:$PREP$SEP)?(?:(?:एक|ek)$SEP)?(?:$MSG$SEP)?$SEND)(?:$SEP$THAT)?$SEP(.+)$",
            RegexOption.IGNORE_CASE,
        )

        /** "Rizwan Travels को WhatsApp पर good morning भेजो" (the message before the verb) */
        private val MESSAGE_FIRST = Regex(
            "^(.+?)$SEP$KO$SEP(?:$WA|$MSG)$SEP(?:$PREP$SEP)?(?:$MSG$SEP)?(.+?)$SEP(?:(?:का|ka|वाला|wala)$SEP)?(?:$MSG$SEP)?$SEND$",
            RegexOption.IGNORE_CASE,
        )

        /** "Rizwan Travels को WhatsApp करना है" / "… को मैसेज भेजो" */
        private val TO_ONLY = Regex(
            "^(?:$WA$SEP(?:$PREP$SEP)?)?(.+?)$SEP$KO$SEP((?:$WA$SEP)?(?:$PREP$SEP)?(?:(?:एक|ek)$SEP)?(?:$MSG$SEP)?(?:$SEND|$WANT))(?:$SEP(?:है|hai|na|ना))?$",
            RegexOption.IGNORE_CASE,
        )

        /** "किसी को WhatsApp करना है" / "WhatsApp पर मैसेज भेजना है" / "WhatsApp करो" */
        private val START = Regex(
            "^(?:(?:किसी|kisi|kisiko|किसीको)(?:$SEP$KO)?$SEP)?(?:$WA|$MSG)(?:$SEP$PREP)?(?:$SEP(?:एक|ek))?(?:$SEP$MSG)?(?:$SEP(?:$SEND|$WANT))(?:$SEP(?:है|hai|na|ना))?$",
            RegexOption.IGNORE_CASE,
        )

        private val STYLE = Regex(
            "(?:^|$SEP)(?:(?:इसे|ise|isko|इसको)$SEP)?(hinglish|हिंग्लिश|हिंगलिश|english|इंग्लिश|अंग्रेजी|अंग्रेज़ी|hindi|हिंदी|हिन्दी)$SEP?(?:में|मे|me|mein|main)" +
                "(?:$SEP(?:लिखो|लिख\\s?के|लिखकर|लिखना|लिख\\s?दो|भेजो|likho|likh\\s?ke|likhkar|likh\\s?do|bhejo))?(?:$SEP$THAT)?",
            RegexOption.IGNORE_CASE,
        )

        private val TELL_HIM = Regex(
            "^(?:(?:उसको|उसे|उनको|उन्हें|इनको|इसको|usko|use|unko|unhe|inko|isko)$SEP)?(?:बोलो|बोल\\s?दो|बोल\\s?दीजिए|कहो|कह\\s?दो|लिखो|लिख\\s?दो|बता\\s?दो|बताओ|bolo|bol\\s?do|kaho|keh\\s?do|likho|likh\\s?do|bata\\s?do|batao)(?:$SEP$THAT)?$SEP",
            RegexOption.IGNORE_CASE,
        )
        private val TELL_HIM_END = Regex("$SEP(?:ऐसा|aisa|ye|ये|यह)?$SEP?(?:बोल\\s?दो|लिख\\s?दो|भेज\\s?दो|बोलो|लिखो|bol\\s?do|likh\\s?do|bhej\\s?do|bolo|likho)$", RegexOption.IGNORE_CASE)
        private val ACK = Regex("^(?:(?:seema|सीमा|हाँ|हां|haan|ठीक\\s?है|theek\\s?hai|ok|okay|ओके|अच्छा|acha|achha|ji|जी)$SEP)+", RegexOption.IGNORE_CASE)
        private val LEADING_THAT = Regex("^(?:कि|की|ki)$SEP", RegexOption.IGNORE_CASE)

        private fun clean(text: String) = text.trim().replace(Regex("\\s+"), " ").trim(',', '.', '!', '?', '।', ' ')

        /** The request in [text], or null when it is not a WhatsApp message request. */
        fun parse(text: String): WhatsAppRequest? {
            var t = clean(text)
            if (t.isEmpty() || t.length > 300 || SMS.containsMatchIn(t) || !HAS_WA_OR_MSG.containsMatchIn(t)) return null
            t = t.replace(LEADING, "").trim()
            START.matchEntire(t)?.let { return WhatsAppRequest(null, null) }
            ONE_SHOT.matchEntire(t)?.takeIf { HAS_WA_OR_MSG.containsMatchIn(it.groupValues[2]) }?.let { m ->
                val name = contactName(m.groupValues[1]) ?: return null
                val (message, style) = message(m.groupValues[3])
                // "… को मैसेज भेजो कि" with nothing after it: ask what to write.
                return WhatsAppRequest(name, message, style)
            }
            TO_ONLY.matchEntire(t)?.takeIf { HAS_WA_OR_MSG.containsMatchIn(it.groupValues[2]) }?.let { m ->
                val name = contactName(m.groupValues[1]) ?: return null
                return WhatsAppRequest(name, null)
            }
            MESSAGE_FIRST.matchEntire(t)?.let { m ->
                val name = contactName(m.groupValues[1])
                val (message, style) = message(m.groupValues[2])
                if (name != null && message != null && !HAS_WA_OR_MSG.matches(message) && !Regex("^(?:$PREP|$WANT|एक|ek)$", RegexOption.IGNORE_CASE).matches(message)) {
                    return WhatsAppRequest(name, message, style)
                }
            }
            return null
        }

        private fun contactName(raw: String): String? {
            val name = clean(raw.replace(LEADING, ""))
            val words = name.split(' ')
            // A whole sentence before "को" is not a name.
            if (name.isEmpty() || words.size > 5 || HAS_WA_OR_MSG.matches(name)) return null
            return name
        }

        /**
         * What to write, from an answer like "उसको बोलो कि कल का बुकिंग का पैसा नहीं आया है" or a one-shot
         * request's tail, with "Hinglish में" taken out as the [MessageStyle]. Null when nothing is left.
         */
        fun message(raw: String): Pair<String?, MessageStyle?> {
            var t = clean(raw)
            var style: MessageStyle? = null
            STYLE.find(t)?.let { m ->
                style = when (m.groupValues[1].lowercase()) {
                    "hinglish", "हिंग्लिश", "हिंगलिश" -> MessageStyle.HINGLISH
                    "english", "इंग्लिश", "अंग्रेजी", "अंग्रेज़ी" -> MessageStyle.ENGLISH
                    else -> MessageStyle.HINDI
                }
                t = clean(t.removeRange(m.range))
            }
            // "ठीक है, उसको बोलो कि …": the "ठीक है" belongs to the conversation, not the message.
            val acked = clean(t.replace(ACK, ""))
            if (acked != t && TELL_HIM.containsMatchIn(acked)) t = acked
            repeat(2) {
                t = clean(t.replace(TELL_HIM, "").replace(LEADING_THAT, "").replace(TELL_HIM_END, ""))
            }
            return t.ifEmpty { null } to style
        }
    }
}

/** Short spoken answers: yes / no, and which of the names read out. */
object VoiceReply {
    private val YES = setOf(
        "हाँ", "हां", "हा", "हाँ जी", "हां जी", "जी", "जी हाँ", "जी हां", "हम्म", "हाँ हाँ", "हां हां", "ठीक है", "हाँ ठीक है", "हां ठीक है", "बिल्कुल", "सही", "सही है", "हाँ सही है", "हां सही है",
        "भेजो", "भेज दो", "हाँ भेजो", "हां भेजो", "हाँ भेज दो", "हां भेज दो", "भेज दीजिए", "कर दो", "हाँ कर दो", "ओके", "ओके भेज दो", "चलो भेजो", "यस",
        "haan", "han", "ha", "haa", "haan ji", "han ji", "ji", "ji haan", "hmm", "theek hai", "thik hai", "bilkul", "sahi", "sahi hai", "bhejo", "bhej do", "haan bhejo", "haan bhej do",
        "kar do", "yes", "yeah", "yep", "ok", "okay", "ok bhej do", "send", "send it", "send karo", "sure", "right", "correct",
    )
    private val NO = setOf(
        "नहीं", "नही", "ना", "नो", "मत", "मत भेजो", "नहीं भेजो", "नहीं नहीं", "रहने दो", "रुको", "कैंसल", "गलत", "गलत है", "बंद करो", "छोड़ो", "छोड़ दो", "नहीं रहने दो",
        "nahi", "nahin", "na", "no", "nope", "mat", "mat bhejo", "rehne do", "rahne do", "ruko", "cancel", "galat", "galat hai", "stop", "chhodo", "chhod do",
    )
    private val NO_START = Regex("^(?:नहीं|नही|ना|no|nahi|nahin)(?:[\\s,.!।]+)", RegexOption.IGNORE_CASE)

    internal fun plain(text: String) = text.lowercase().replace(Regex("[,.!?।\"']"), " ").replace(Regex("\\s+"), " ").trim()
        .removePrefix("सीमा ").removePrefix("seema ").removeSuffix(" सीमा").removeSuffix(" seema").trim()

    /** true = yes, false = no, null = something else (a correction, a name, noise). */
    fun yesNo(text: String): Boolean? {
        val t = plain(text)
        return when {
            t in YES -> true
            t in NO -> false
            else -> null
        }
    }

    /** What follows a leading "नहीं," ("नहीं, लिखो कि कल आऊँगा" -> "लिखो कि कल आऊँगा"), or null. */
    fun afterNo(text: String): String? {
        val t = text.trim()
        val m = NO_START.find(t) ?: return null
        return t.substring(m.range.last + 1).trim().ifEmpty { null }
    }

    private val ORDINALS = listOf(
        listOf("पहला", "पहले", "पहली", "पहले वाला", "first", "pehla", "pahla", "pehle", "pahle", "pehli", "1", "एक", "ek", "one", "number 1", "नंबर 1", "नंबर एक"),
        listOf("दूसरा", "दूसरे", "दूसरी", "second", "doosra", "dusra", "dusre", "doosre", "2", "दो", "two", "number 2", "नंबर 2", "नंबर दो"),
        listOf("तीसरा", "तीसरे", "तीसरी", "third", "teesra", "tisra", "teesre", "3", "तीन", "teen", "three", "number 3", "नंबर 3", "नंबर तीन"),
        listOf("चौथा", "चौथे", "चौथी", "fourth", "chautha", "chauthe", "4", "चार", "char", "chaar", "four", "number 4", "नंबर 4", "नंबर चार"),
        listOf("पाँचवाँ", "पांचवा", "पाँचवा", "पांचवें", "fifth", "panchva", "5", "पाँच", "पांच", "paanch", "five", "number 5", "नंबर 5"),
    )
    private val WALA = Regex("\\s*(?:वाला|वाले|वाली|wala|wale|wali|वाला नाम|nambar|वाले को|wale ko|को|ko)$")

    /** "दूसरा" / "2 नंबर" / "second wala" -> 1 (0-based), when [count] names were offered. */
    fun choice(text: String, count: Int): Int? {
        var t = plain(text).replace(WALA, "").trim()
        t = t.removeSuffix(" नंबर").removeSuffix(" number").trim()
        val i = ORDINALS.indexOfFirst { t in it }
        return i.takeIf { it in 0 until count }
    }
}
