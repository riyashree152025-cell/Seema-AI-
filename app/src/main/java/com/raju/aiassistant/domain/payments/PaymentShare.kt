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
        "व्हाट्सएप", "पर", "पे", "আমার", "সীমা",
    )

    private fun hasWord(text: String, pattern: String) = Regex("(?<![\\p{L}\\p{M}])$pattern", RegexOption.IGNORE_CASE).containsMatchIn(text)

    /** The amount said in rupees ("500 rupaye", "₹1,200"), or null. */
    fun amount(text: String): Long? = AMOUNT.find(text.replace(",", ""))?.let { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).toLongOrNull() }

    /** The person before "ko": up to three words, without the words around it ("mera QR", "UPI ID"). */
    fun contactBeforeKo(text: String): String? {
        val t = text.replace(Regex("[,.!?।]"), " ").replace(Regex("\\s+"), " ").trim()
        val m = TO.find(t) ?: return null
        val before = t.substring(0, m.range.first).trim().split(' ').filter { it.isNotBlank() }
        val name = mutableListOf<String>()
        for (w in before.asReversed()) {
            val lw = w.lowercase()
            if (lw in FILLER || Regex("^$QR$|^$UPI$|^$SEND\\S*$", RegexOption.IGNORE_CASE).matches(lw) || lw.any { it.isDigit() } || lw.startsWith("₹")) break
            name.add(0, w)
            if (name.size == 3) break
        }
        return name.joinToString(" ").takeIf { it.length >= 2 }
    }

    /** "Mera QR bhej do Rizwan ko", "Rizwan ko payment QR bhejo 500 rupaye"; null when it is not this request. */
    fun qrRequest(text: String): QrRequest? {
        if (!hasWord(text, QR) || !hasWord(text, SEND) || text.length > 140) return null
        val who = contactBeforeKo(text) ?: return null
        return QrRequest(who, amount(text))
    }

    /** "Jalal bhai ko UPI ID bhej do", "PhonePe wala UPI Anand ko bhejo"; null when it is not this request. */
    fun upiRequest(text: String, saved: List<UpiEntry>): UpiRequest? {
        if (!hasWord(text, UPI) || !hasWord(text, SEND) || text.length > 140) return null
        val label = pick(saved, text)?.label
        val withoutLabel = label?.let { l -> text.replace(Regex(Regex.escape(l), RegexOption.IGNORE_CASE), " ") } ?: text
        val who = contactBeforeKo(withoutLabel) ?: return null
        return UpiRequest(who, label, amount(text))
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
