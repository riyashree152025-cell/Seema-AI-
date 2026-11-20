package com.raju.aiassistant.domain.memory

/**
 * Long-term facts Raju tells about himself in passing — "mera bhai Amit hai", "मेरी wife का नाम Priya है",
 * "mujhe adrak wali chai pasand hai", "আমার ভাইয়ের নাম অমিত", "আমার চা খুব ভালো লাগে". Picked out on the phone (no AI),
 * saved quietly in Seema's memory (Memory Center → View / Clear), and given to her with every chat so she
 * remembers. Questions ("mera bhai kaun hai?") and passwords / codes are never saved.
 */
object FactExtractor {
    private const val RELATIONS_HI =
        "bhai|bhaiya|behen|bahan|didi|papa|pitaji|maa|mummy|mammi|wife|biwi|patni|husband|pati|beta|beti|bete|dost|boss|" +
            "sala|jija|chacha|chachi|mama|mami|nana|nani|dada|dadi|bhabhi|partner|" +
            "भाई|भैया|बहन|दीदी|पापा|पिताजी|माँ|मां|मम्मी|वाइफ|बीवी|पत्नी|पति|बेटा|बेटी|दोस्त|बॉस|साला|जीजा|चाचा|चाची|मामा|मामी|नाना|नानी|दादा|दादी|भाभी"
    private const val RELATIONS_BN = "ভাই|দাদা|বোন|দিদি|বাবা|মা|বউ|স্ত্রী|বর|স্বামী|ছেলে|মেয়ে|বন্ধু|বস|মামা|কাকা|জামাইবাবু|বৌদি"

    private val QUESTION = Regex("(?<![\\p{L}\\p{M}])(?:kaun|kya|kahan|kab|kaisa|कौन|क्या|कहाँ|कब|कैसा|কে|কী|কি|কোথায়|কবে)(?![\\p{L}\\p{M}])|\\?", RegexOption.IGNORE_CASE)

    // "mera bhai Amit hai", "meri wife ka naam Priya hai", "मेरे पापा का नाम रमेश है"
    private val RELATION_HI = Regex(
        "^(?:mera|meri|mere|मेरा|मेरी|मेरे)\\s+($RELATIONS_HI)(?:\\s+(?:ka|ki|ke|का|की|के)\\s+(?:naam|नाम))?\\s+(.{2,30}?)\\s+(?:hai|hain|he|है|हैं)$",
        RegexOption.IGNORE_CASE,
    )
    // "আমার ভাইয়ের নাম অমিত" (only with "নাম": "আমার মা অসুস্থ" is not a name)
    private val RELATION_BN = Regex("^আমার\\s+($RELATIONS_BN)(?:য়ের|ের|র)?\\s+নাম\\s+(.{2,30}?)$")
    // "mujhe chai pasand hai", "mujhe karela pasand nahi hai", "मुझे चाय बहुत पसंद है"
    private val LIKE_HI = Regex(
        "^(?:mujhe|muje|मुझे)\\s+(.{2,40}?)\\s+(?:bahut\\s+|bohot\\s+|बहुत\\s+)?(?:pasand|पसंद|achha lagta|अच्छा लगता|अच्छी लगती|achhi lagti)\\s*(nahi|nahin|नहीं)?\\s*(?:hai|है)?$",
        RegexOption.IGNORE_CASE,
    )
    // "আমার চা খুব ভালো লাগে", "আমার করলা ভালো লাগে না"
    private val LIKE_BN = Regex("^আমার\\s+(.{2,40}?)\\s+(?:খুব\\s+)?(?:ভালো|ভাল)\\s+লাগে\\s*(না)?$")

    private val NOT_A_NAME = Regex(
        "(?<![\\p{L}\\p{M}])(?:nahi|nahin|aaya|aayi|gaya|gayi|raha|rahi|rahe|hua|hui|tha|thi|mein|me|par|pe|se|ko|ne|bimar|bahut|ghar|" +
            "नहीं|आया|आई|गया|गई|रहा|रही|हुआ|हुई|था|थी|में|पर|से|को|ने|बीमार|बहुत|घर)(?![\\p{L}\\p{M}])",
        RegexOption.IGNORE_CASE,
    )

    private fun clean(text: String) = text.trim().trimEnd('.', '!', '।', ' ').replace(Regex("\\s+"), " ")
        .replace(Regex("^(?:seema|सीमा|সীমা)[,\\s]+", RegexOption.IGNORE_CASE), "")

    private fun ok(value: String) = value.isNotBlank() && value.split(' ').size <= 5 && !QUESTION.containsMatchIn(value)

    /** The fact in [said], as the short line saved in memory, or null. */
    fun extract(said: String): String? {
        val t = clean(said)
        if (t.length < 6 || t.length > 120 || QUESTION.containsMatchIn(t)) return null
        RELATION_HI.matchEntire(t)?.let { m ->
            val who = m.groupValues[2].trim()
            // A name is one to three words, not a happening ("ghar aaya", "school mein", "bimar").
            if (!ok(who) || who.split(' ').size > 3 || NOT_A_NAME.containsMatchIn(who)) return null
            return "Raju का ${m.groupValues[1].lowercase()}: $who"
        }
        RELATION_BN.matchEntire(t)?.let { m ->
            val who = m.groupValues[2].trim().removeSuffix(" আছে")
            return if (ok(who) && !who.endsWith("না")) "Raju-র ${m.groupValues[1]}: $who" else null
        }
        LIKE_HI.matchEntire(t)?.let { m ->
            val what = m.groupValues[1].trim()
            if (!ok(what)) return null
            return if (m.groupValues[2].isNotEmpty()) "Raju को पसंद नहीं: $what" else "Raju को पसंद है: $what"
        }
        LIKE_BN.matchEntire(t)?.let { m ->
            val what = m.groupValues[1].trim()
            if (!ok(what)) return null
            return if (m.groupValues[2].isNotEmpty()) "Raju-র ভালো লাগে না: $what" else "Raju-র ভালো লাগে: $what"
        }
        return null
    }

    /**
     * A newer fact replaces an older one on the same subject ("Raju का भाई: Amit" → "Raju का भाई: Sumit"), and a like
     * replaces the opposite dislike. The saved fact whose subject [fact] shares, or null.
     */
    fun subject(fact: String): String =
        fact.substringBefore(':').replace("पसंद नहीं", "पसंद है").replace("ভালো লাগে না", "ভালো লাগে") +
            if (fact.contains("पसंद") || fact.contains("ভালো লাগে")) ":" + fact.substringAfter(':').trim().lowercase() else ""
}
