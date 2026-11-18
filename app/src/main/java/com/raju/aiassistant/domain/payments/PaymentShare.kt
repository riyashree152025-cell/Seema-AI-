package com.raju.aiassistant.domain.payments

import com.raju.aiassistant.domain.tools.Romanize

/** A saved UPI ID with the name it is asked by ("Paytm", "PhonePe", "R Travels"). */
data class UpiEntry(val label: String, val upiId: String)

/** "Mera QR bhej do Rizwan ko (500 rupaye)": who gets the payment QR, and the amount if one was said. */
data class QrRequest(val contact: String, val amountRupees: Long?)

/** "Jalal bhai ko UPI ID bhej do (Paytm wala)": who, and which saved ID if it was named. */
data class UpiRequest(val contact: String, val label: String?, val amountRupees: Long?)

/**
 * Sharing R Travels' payment details on WhatsApp by voice: the saved payment QR photo, or one of several saved
 * UPI IDs (asked "kaun si?" when more than one is saved and none was named). Parsing and texts only; the
 * engine finds the contact, confirms and sends.
 */
object PaymentShare {
    private const val QR = "(?:qr|q r|क्यूआर|क्यू आर|क्यूआर कोड|কিউআর|scanner|स्कैनर)"
    private const val UPI = "(?:upi|u p i|यूपीआई|यू पी आई|यूपीआय|ইউপিআই)"
    private const val SEND = "(?:bhej|bhejo|bhejna|send|भेज|भेजो|भेजना|भेजिए|পাঠা|পাঠাও|দাও)"
    private val TO = Regex("(?<![\\p{L}\\p{M}])(?:ko|को|কে)(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE)
    private val AMOUNT = Regex("(?:₹|rs\\.?\\s*)(\\d{1,7})|(\\d{1,7})\\s*(?:rupaye|rupaiya|rupees|rupee|rs|रुपये|रुपए|रुपया|টাকা)", RegexOption.IGNORE_CASE)

    /** Words around the name that are not the name. */
    private val FILLER = setOf(
        "mera", "meri", "mere", "apna", "apni", "payment", "code", "id", "aaidi", "wala", "vala", "ka", "ki", "ke", "bhi", "zara", "jara",
        "seema", "please", "plz", "abhi", "jaldi", "do", "de", "dena", "karo", "whatsapp", "par", "pe", "on",
        "मेरा", "मेरी", "मेरे", "अपना", "पेमेंट", "कोड", "आईडी", "वाला", "का", "की", "के", "भी", "ज़रा", "सीमा", "अभी", "दो", "दे", "देना", "करो",
        "व्हाट्सएप", "पर", "पे", "আমার", "সীমা", "paytm", "phonepe", "gpay", "pe", "पेटीएम", "फोनपे", "फ़ोनपे", "वाला", "वाली",
    )

    private fun hasWord(text: String, pattern: String) = Regex("(?<![\\p{L}\\p{M}])$pattern", RegexOption.IGNORE_CASE).containsMatchIn(text)

    /** The amount said in rupees ("500 rupaye", "₹1,200", "हज़ार रुपए", "do hazaar", "डेढ़ हज़ार"), or null. */
    fun amount(text: String): Long? {
        AMOUNT.find(text.replace(",", ""))?.let { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).toLongOrNull()?.let { return it } }
        val m = WORD_AMOUNT.find(text) ?: return null
        val count = m.groupValues[1].trim().lowercase().let { w -> if (w.isEmpty()) 1.0 else w.toDoubleOrNull() ?: NUMBER_WORDS[w] ?: return null }
        val unit = when (m.groupValues[2].lowercase()) {
            "sau", "सौ" -> 100.0
            "lakh", "लाख" -> 100_000.0
            else -> 1_000.0
        }
        return (count * unit).toLong().takeIf { it > 0 }
    }

    private val NUMBER_WORDS = mapOf(
        "ek" to 1.0, "एक" to 1.0, "do" to 2.0, "दो" to 2.0, "teen" to 3.0, "तीन" to 3.0, "char" to 4.0, "चार" to 4.0,
        "paanch" to 5.0, "panch" to 5.0, "पांच" to 5.0, "पाँच" to 5.0, "chhe" to 6.0, "छह" to 6.0, "saat" to 7.0, "सात" to 7.0,
        "aath" to 8.0, "आठ" to 8.0, "nau" to 9.0, "नौ" to 9.0, "das" to 10.0, "दस" to 10.0,
        "dedh" to 1.5, "डेढ़" to 1.5, "डेढ" to 1.5, "dhai" to 2.5, "ढाई" to 2.5, "sava" to 1.25, "सवा" to 1.25,
    )
    private val WORD_AMOUNT = Regex(
        "(?<![\\p{L}\\p{M}])((?:\\d+(?:\\.\\d+)?|ek|एक|do|दो|teen|तीन|char|चार|paanch|panch|पांच|पाँच|chhe|छह|saat|सात|aath|आठ|nau|नौ|das|दस|dedh|डेढ़|डेढ|dhai|ढाई|sava|सवा)\\s+)?(hazaar|hazar|हज़ार|हजार|sau|सौ|lakh|लाख)(?![\\p{L}\\p{M}])",
        RegexOption.IGNORE_CASE,
    )

    /** The person before "ko": up to three words, without the words around it ("mera QR", "UPI ID"). */
    fun contactBeforeKo(text: String): String? {
        val t = text.replace(Regex("[,.!?।]"), " ").replace(Regex("\\s+"), " ").trim()
        val m = TO.find(t) ?: return null
        val before = t.substring(0, m.range.first).trim().split(' ').filter { it.isNotBlank() }
        val name = mutableListOf<String>()
        for (w in before.asReversed()) {
            val lw = w.lowercase()
            // Digits stay: a contact can be saved as "Vodafone 01". Money words end the name.
            if (lw in FILLER || Regex("^$QR$|^$UPI$|^$SEND\\S*$", RegexOption.IGNORE_CASE).matches(lw) || lw.startsWith("₹") || MONEY_WORD.matches(lw)) break
            name.add(0, w)
            if (name.size == 3) break
        }
        return name.joinToString(" ").takeIf { it.length >= 2 }
    }

    /**
     * "Mera QR bhej do Rizwan ko", "Rizwan ko payment QR bhejo 500 rupaye", "Paytm ka QR bhej do Rizwan bhai",
     * "mera QR bhejo" (no name: [QrRequest.contact] is empty and Seema asks whom). Null when it is not this request.
     */
    fun qrRequest(text: String): QrRequest? {
        if (!hasWord(text, QR) || !hasWord(text, SEND) || text.length > 200) return null
        return QrRequest(contactOf(text).orEmpty(), amount(text))
    }

    /** The person: before "ko"; only when there is no "ko" at all, the words after the send verb ("bhej do Rizwan bhai"). */
    fun contactOf(text: String): String? = if (TO.containsMatchIn(text)) contactBeforeKo(text) else contactAfterVerb(text)

    private val MONEY_WORD = Regex("^(?:rupaye|rupaiya|rupees|rupee|rs|रुपये|रुपए|रुपया|टका|টাকা|hazaar|hazar|हज़ार|हजार|sau|सौ|lakh|लाख)$", RegexOption.IGNORE_CASE)

    private fun contactAfterVerb(text: String): String? {
        val t = text.replace(Regex("[,.!?।]"), " ").replace(Regex("\\s+"), " ").trim()
        val words = t.split(' ')
        val verbAt = words.indexOfLast { Regex("^$SEND\\S*$", RegexOption.IGNORE_CASE).matches(it) }
        if (verbAt < 0) return null
        val name = words.drop(verbAt + 1).filter { w ->
            val lw = w.lowercase()
            lw !in FILLER && !Regex("^$QR$|^$UPI$|^$SEND\\S*$", RegexOption.IGNORE_CASE).matches(lw) && lw.none { it.isDigit() } && !lw.startsWith("₹") &&
                !Regex("^(?:rupaye|rupees|rs|रुपये|रुपए|টাকা|dena|दे|दो|do|de)$", RegexOption.IGNORE_CASE).matches(lw)
        }
        return name.take(3).joinToString(" ").takeIf { it.length >= 2 }
    }

    /** "Jalal bhai ko UPI ID bhej do", "PhonePe wala UPI Anand ko bhejo"; null when it is not this request. */
    fun upiRequest(text: String, saved: List<UpiEntry>): UpiRequest? {
        if (!hasWord(text, UPI) || !hasWord(text, SEND) || text.length > 200) return null
        val label = pick(saved, text)?.label
        val withoutLabel = label?.let { l -> text.replace(Regex(Regex.escape(l), RegexOption.IGNORE_CASE), " ") } ?: text
        return UpiRequest(contactOf(withoutLabel).orEmpty(), label, amount(text))
    }

    /** The saved ID a reply names ("Paytm wala", "फोनपे भेज दो", "pehla"), or null. */
    fun pick(saved: List<UpiEntry>, reply: String): UpiEntry? {
        if (saved.isEmpty()) return null
        val said = " ${Romanize.phonetic(reply)} "
        saved.firstOrNull { e -> Romanize.phonetic(e.label).let { it.isNotBlank() && said.contains(" $it ") || said.replace(" ", "").contains(it.replace(" ", "")) } }?.let { return it }
        // Spelled differently ("फोनपे" = fonpe, "PhonePe" = fonepe): the consonants decide.
        val saidSkeleton = skeleton(said)
        saved.firstOrNull { e -> skeleton(Romanize.phonetic(e.label)).let { it.length >= 3 && saidSkeleton.contains(it) } }?.let { return it }
        val ordinal = ORDINALS.indexOfFirst { o -> Regex("(?<![\\p{L}\\p{M}])(?:$o)(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE).containsMatchIn(reply) }
        return if (ordinal in saved.indices) saved[ordinal] else null
    }

    /** A name's first letter and its consonants, spaces removed ("fonepe" → "fnp"). */
    private fun skeleton(s: String): String {
        val t = s.replace(" ", "")
        if (t.isEmpty()) return ""
        return t.first() + t.drop(1).filter { it !in "aeiou" }
    }

    private val ORDINALS = listOf(
        "pehla|pehli|pahla|first|1st|पहला|पहली|প্রথম|ek number",
        "dusra|doosra|dusri|second|2nd|दूसरा|दूसरी|দ্বিতীয়",
        "teesra|tisra|third|3rd|तीसरा|तीसरी|তৃতীয়",
    )

    /** "Jalal bhai ko kaun si UPI ID bhejni hai — Paytm, PhonePe ya R Travels?" */
    fun whichQuestion(contact: String, saved: List<UpiEntry>): String {
        val labels = saved.map { it.label }
        val list = if (labels.size <= 1) labels.joinToString() else labels.dropLast(1).joinToString(", ") + " या " + labels.last()
        return "$contact को कौन सी UPI ID भेजनी है — $list?"
    }

    /** The QR's WhatsApp caption. */
    fun qrCaption(amountRupees: Long?): String = buildString {
        append("💳 *R Travels - Payment QR*\nPlease scan this QR code to make the payment.")
        amountRupees?.let { append("\n*Amount:* ₹$it") }
    }

    /** The UPI message sent on WhatsApp. */
    fun upiMessage(entry: UpiEntry, payeeName: String, amountRupees: Long?): String = buildString {
        append("💳 *R Travels - Payment Details*\n*UPI ID:* ${entry.upiId}")
        if (payeeName.isNotBlank()) append("\n*Name:* $payeeName")
        amountRupees?.let { append("\n*Amount:* ₹$it") }
    }

    /** A UPI ID as people write it: name@bank. */
    fun isValidUpi(id: String): Boolean = Regex("^[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9.-]{1,32}$").matches(id.trim())

    /** Saved as one line per entry ("label\tid"). */
    fun encode(list: List<UpiEntry>): String = list.joinToString("\n") { "${it.label.replace('\t', ' ').replace('\n', ' ')}\t${it.upiId.trim()}" }

    fun decode(raw: String?): List<UpiEntry> = raw.orEmpty().lines().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) UpiEntry(parts[0].trim(), parts[1].trim()) else null
    }
}
