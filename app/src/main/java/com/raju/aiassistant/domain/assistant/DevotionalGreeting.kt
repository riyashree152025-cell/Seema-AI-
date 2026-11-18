package com.raju.aiassistant.domain.assistant

/**
 * "राधे राधे" → "राधे राधे", "जय श्री कृष्णा" → "जय श्री कृष्णा", "राम राम" → "राम राम": a devotional greeting is
 * answered with the same greeting, at once, on the phone (no AI, no waiting). The reply is in the script the
 * greeting came in (Bengali stays Bengali). Only when the sentence is just the greeting (with "Seema", "ji",
 * "bolo" around it): "radhe radhe, aaj ka mausam batao" goes on as a normal request.
 */
object DevotionalGreeting {
    private class Greeting(val pattern: String, val hindi: String, val bengali: String)

    private val GREETINGS = listOf(
        Greeting("radhe\\s*radhe|radhey\\s*radhey|radhe\\s*shyam|राधे\\s*राधे|राधे\\s*श्याम|রাধে\\s*রাধে", "राधे राधे", "রাধে রাধে"),
        Greeting("jai\\s*shr[ie]+\\s*krishna?|jay\\s*shr[ie]+\\s*krishna?|जय\\s*श्री\\s*कृष्णा?|जय\\s*श्रीकृष्ण|জয়\\s*শ্রী\\s*কৃষ্ণ", "जय श्री कृष्णा", "জয় শ্রী কৃষ্ণ"),
        Greeting("hare\\s*krishna?(?:\\s*hare\\s*krishna?)?|हरे\\s*कृष्णा?(?:\\s*हरे\\s*कृष्णा?)?|হরে\\s*কৃষ্ণ(?:\\s*হরে\\s*কৃষ্ণ)?", "हरे कृष्णा", "হরে কৃষ্ণ"),
        Greeting("jai\\s*shr[ie]+\\s*ram|jay\\s*shr[ie]+\\s*ram|जय\\s*श्री\\s*राम|জয়\\s*শ্রী\\s*রাম", "जय श्री राम", "জয় শ্রী রাম"),
        Greeting("jai\\s*siya\\s*ram|jay\\s*siya\\s*ram|जय\\s*सिया\\s*राम|জয়\\s*সিয়া\\s*রাম", "जय सिया राम", "জয় সিয়া রাম"),
        Greeting("sita\\s*ram|seeta\\s*ram|सीता\\s*राम|सीताराम", "सीता राम", "সীতা রাম"),
        Greeting("ram\\s*ram|raam\\s*raam|राम\\s*राम|রাম\\s*রাম", "राम राम", "রাম রাম"),
        Greeting("har\\s*har\\s*mahadev|हर\\s*हर\\s*महादेव|হর\\s*হর\\s*মহাদেব", "हर हर महादेव", "হর হর মহাদেব"),
        Greeting("jai\\s*bhole(?:\\s*nath)?|jai\\s*bholenath|जय\\s*भोले(?:\\s*नाथ)?|जय\\s*भोलेनाथ|bam\\s*bam\\s*bhole|बम\\s*बम\\s*भोले", "जय भोलेनाथ", "জয় ভোলেনাথ"),
        Greeting("om\\s*namah?\\s*shivaye?|ॐ\\s*नमः?\\s*शिवाय|ओम\\s*नमः?\\s*शिवाय|ওঁ\\s*নমঃ?\\s*শিবায়", "ॐ नमः शिवाय", "ওঁ নমঃ শিবায়"),
        Greeting("jai\\s*mata\\s*di|jay\\s*mata\\s*di|जय\\s*माता\\s*दी|জয়\\s*মাতা\\s*দি", "जय माता दी", "জয় মাতা দি"),
        Greeting("jai\\s*(?:maa|ma)\\s*durga|जय\\s*मां?\\s*दुर्गा|জয়\\s*মা\\s*দুর্গা", "जय माँ दुर्गा", "জয় মা দুর্গা"),
        Greeting("jai\\s*(?:maa|ma)\\s*kali|जय\\s*मां?\\s*काली|জয়\\s*মা\\s*কালী", "जय माँ काली", "জয় মা কালী"),
        Greeting("jai\\s*(?:bajrang\\s*bali|bajrangbali|hanuman)|जय\\s*(?:बजरंग\\s*बली|बजरंगबली|हनुमान)|জয়\\s*(?:বজরংবলী|হনুমান)", "जय बजरंगबली", "জয় বজরংবলী"),
        Greeting("ganpati\\s*bappa\\s*mor[iy]a|गणपति\\s*बप्पा\\s*मोरया|গণপতি\\s*বাপ্পা\\s*মোরিয়া", "गणपति बप्पा मोरया, मंगलमूर्ति मोरया", "গণপতি বাপ্পা মোরিয়া"),
        Greeting("jai\\s*ganesh|जय\\s*गणेश|জয়\\s*গণেশ", "जय गणेश", "জয় গণেশ"),
        Greeting("jai\\s*jagannath|जय\\s*जगन्नाथ|জয়\\s*জগন্নাথ", "जय जगन्नाथ", "জয় জগন্নাথ"),
        Greeting("jai\\s*shr[ie]+\\s*shyam|जय\\s*श्री\\s*श्याम", "जय श्री श्याम", "জয় শ্রী শ্যাম"),
        Greeting("jai\\s*baba\\s*loknath|জয়\\s*বাবা\\s*লোকনাথ|जय\\s*बाबा\\s*लोकनाथ", "जय बाबा लोकनाथ", "জয় বাবা লোকনাথ"),
        Greeting("jai\\s*gurudev|जय\\s*गुरुदेव|জয়\\s*গুরুদেব", "जय गुरुदेव", "জয় গুরুদেব"),
        Greeting("jai\\s*hind|जय\\s*हिंद|জয়\\s*হিন্দ", "जय हिंद", "জয় হিন্দ"),
        Greeting("jai\\s*shr[ie]+\\s*mahakal|जय\\s*श्री\\s*महाकाल|जय\\s*महाकाल", "जय श्री महाकाल", "জয় শ্রী মহাকাল"),
        Greeting("(?:as+alam|as+alaam|salam|salaam)\\s*(?:u|o|w)?\\s*(?:alaikum|alaykum|alekum|walaikum)|अस्सलाम\\s*(?:ु|ो)?\\s*(?:अलैकुम|वालेकुम|वालैकुम)|सलाम\\s*(?:अलैकुम|वालेकुम)|আসসালামু\\s*আলাইকুম", "वालेकुम अस्सलाम", "ওয়ালাইকুম আসসালাম"),
        Greeting("sat\\s*sh?ri\\s*akal|सत\\s*श्री\\s*अकाल|সত\\s*শ্রী\\s*আকাল", "सत श्री अकाल", "সত শ্রী আকাল"),
        Greeting("waheguru\\s*ji\\s*ka\\s*khalsa(?:\\s*waheguru\\s*ji\\s*ki\\s*fateh)?|वाहेगुरु\\s*जी\\s*का\\s*खालसा(?:\\s*वाहेगुरु\\s*जी\\s*की\\s*फतेह)?", "वाहेगुरु जी का खालसा, वाहेगुरु जी की फतेह", "ওয়াহেগুরু জি কা খালসা, ওয়াহেগুরু জি কি ফতেহ"),
        Greeting("jai\\s*jinendra|जय\\s*जिनेंद्र", "जय जिनेंद्र", "জয় জিনেন্দ্র"),
        Greeting("namo\\s*buddhaya|नमो\\s*बुद्धाय|जय\\s*भीम|jai\\s*bhim", "नमो बुद्धाय, जय भीम", "নমো বুদ্ধায়"),
    ).map { g -> Regex("^(?:${g.pattern})$", RegexOption.IGNORE_CASE) to g }

    // Around the greeting: her name, "ji", "bolo", "seema ji", repeats ("radhe radhe radhe radhe").
    private val AROUND = Regex(
        "^(?:(?:hey|hi|he|हे|ও)\\s+)?(?:seema|सीमा|সীমা)\\s*(?:ji|जी|জি)?[,\\s]+|[,\\s]+(?:seema|सीमा|সীমা)(?:\\s*(?:ji|जी|জি))?$|" +
            "[,\\s]+(?:ji|जी|জি|bolo|बोलो|বলো|boliye|बोलिए|ho|हो|hai|है)$",
        RegexOption.IGNORE_CASE,
    )

    /** The answer to [text] when it is only a devotional greeting; null otherwise. */
    fun reply(text: String): String? {
        var t = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
            .replace(Regex("[!।.?🙏,]+"), " ").replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty() || t.length > 80) return null
        // Strip "Seema", "ji", "bolo" around it (a few times: "Seema ji, radhe radhe ji").
        repeat(4) { t = t.replace(AROUND, " ").trim() }
        val bengali = t.any { it in 'ঀ'..'৿' }
        // "Radhe radhe radhe radhe": the same greeting said twice is still that greeting.
        val candidates = listOf(t, halfIfRepeated(t))
        for (c in candidates) {
            GREETINGS.firstOrNull { (re, _) -> re.matches(c) }?.let { (_, g) -> return if (bengali) g.bengali else g.hindi }
        }
        return null
    }

    private fun halfIfRepeated(t: String): String {
        val words = t.split(' ')
        if (words.size % 2 != 0 || words.size < 2) return t
        val half = words.size / 2
        return if (words.subList(0, half) == words.subList(half, words.size)) words.subList(0, half).joinToString(" ") else t
    }
}
