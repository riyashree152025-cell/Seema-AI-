package com.raju.aiassistant.domain.language

/**
 * Amounts as a person says them: "8,985 रुपये" → "आठ हज़ार नौ सौ पचासी रुपये", "22,753 টাকা" →
 * "বাইশ হাজার সাতশো তিপ্পান্ন টাকা". Voices read digits with commas badly (one digit at a time, or "comma"), and slowly;
 * the words are what Raju wants to hear. Indian grouping: हज़ार / लाख / करोड़ (হাজার / লাখ / কোটি).
 */
object NumberWords {
    private val HINDI = listOf(
        "शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ",
        "दस", "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस",
        "बीस", "इक्कीस", "बाईस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस",
        "तीस", "इकतीस", "बत्तीस", "तैंतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस",
        "चालीस", "इकतालीस", "बयालीस", "तैंतालीस", "चवालीस", "पैंतालीस", "छियालीस", "सैंतालीस", "अड़तालीस", "उनचास",
        "पचास", "इक्यावन", "बावन", "तिरपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ",
        "साठ", "इकसठ", "बासठ", "तिरसठ", "चौंसठ", "पैंसठ", "छियासठ", "सड़सठ", "अड़सठ", "उनहत्तर",
        "सत्तर", "इकहत्तर", "बहत्तर", "तिहत्तर", "चौहत्तर", "पचहत्तर", "छिहत्तर", "सतहत्तर", "अठहत्तर", "उन्यासी",
        "अस्सी", "इक्यासी", "बयासी", "तिरासी", "चौरासी", "पचासी", "छियासी", "सत्तासी", "अट्ठासी", "नवासी",
        "नब्बे", "इक्यानबे", "बानबे", "तिरानबे", "चौरानबे", "पचानबे", "छियानबे", "सत्तानबे", "अट्ठानबे", "निन्यानबे",
    )
    private val BENGALI = listOf(
        "শূন্য", "এক", "দুই", "তিন", "চার", "পাঁচ", "ছয়", "সাত", "আট", "নয়",
        "দশ", "এগারো", "বারো", "তেরো", "চোদ্দো", "পনেরো", "ষোলো", "সতেরো", "আঠারো", "উনিশ",
        "কুড়ি", "একুশ", "বাইশ", "তেইশ", "চব্বিশ", "পঁচিশ", "ছাব্বিশ", "সাতাশ", "আটাশ", "উনত্রিশ",
        "তিরিশ", "একত্রিশ", "বত্রিশ", "তেত্রিশ", "চৌত্রিশ", "পঁয়ত্রিশ", "ছত্রিশ", "সাঁইত্রিশ", "আটত্রিশ", "উনচল্লিশ",
        "চল্লিশ", "একচল্লিশ", "বিয়াল্লিশ", "তেতাল্লিশ", "চুয়াল্লিশ", "পঁয়তাল্লিশ", "ছেচল্লিশ", "সাতচল্লিশ", "আটচল্লিশ", "উনপঞ্চাশ",
        "পঞ্চাশ", "একান্ন", "বাহান্ন", "তিপ্পান্ন", "চুয়ান্ন", "পঞ্চান্ন", "ছাপ্পান্ন", "সাতান্ন", "আটান্ন", "উনষাট",
        "ষাট", "একষট্টি", "বাষট্টি", "তেষট্টি", "চৌষট্টি", "পঁয়ষট্টি", "ছেষট্টি", "সাতষট্টি", "আটষট্টি", "উনসত্তর",
        "সত্তর", "একাত্তর", "বাহাত্তর", "তিয়াত্তর", "চুয়াত্তর", "পঁচাত্তর", "ছিয়াত্তর", "সাতাত্তর", "আটাত্তর", "উনআশি",
        "আশি", "একাশি", "বিরাশি", "তিরাশি", "চুরাশি", "পঁচাশি", "ছিয়াশি", "সাতাশি", "আটাশি", "উননব্বই",
        "নব্বই", "একানব্বই", "বিরানব্বই", "তিরানব্বই", "চুরানব্বই", "পঁচানব্বই", "ছিয়ানব্বই", "সাতানব্বই", "আটানব্বই", "নিরানব্বই",
    )
    /** Bengali hundreds are one word: একশো, দুশো … নশো. */
    private val BENGALI_HUNDREDS = listOf("", "একশো", "দুশো", "তিনশো", "চারশো", "পাঁচশো", "ছশো", "সাতশো", "আটশো", "নশো")

    /** [n] in words (0 up to 99,99,99,999); null when it is out of that range. */
    fun words(n: Long, bengali: Boolean): String? {
        if (n < 0 || n > 9_99_99_99_999L) return null
        val small = if (bengali) BENGALI else HINDI
        if (n == 0L) return small[0]
        val parts = mutableListOf<String>()
        var rest = n
        fun group(size: Long, hi: String, bn: String) {
            val count = rest / size
            if (count > 0) {
                parts += (words(count, bengali) ?: return) + " " + (if (bengali) bn else hi)
                rest %= size
            }
        }
        group(1_00_00_000L, "करोड़", "কোটি")
        group(1_00_000L, "लाख", "লাখ")
        group(1_000L, "हज़ार", "হাজার")
        val hundreds = (rest / 100).toInt()
        if (hundreds > 0) parts += if (bengali) BENGALI_HUNDREDS[hundreds] else "${HINDI[hundreds]} सौ"
        val tail = (rest % 100).toInt()
        if (tail > 0) parts += small[tail]
        return parts.joinToString(" ")
    }

    // "8,985 रुपये", "₹ नहीं" (already turned into "रुपये" before), "3,750.75 रुपये", "22,753 টাকা".
    private val AMOUNT = Regex("(?<![\\d.,])(\\d{1,3}(?:,\\d{2,3})+|\\d+)(?:\\.(\\d{1,2}))?\\s*(रुपये|रुपए|रुपया|रुपयों|টাকা)")

    /**
     * Every amount with its currency word spelled out in that word's language ("रुपये" → Hindi words, "টাকা" →
     * Bengali words); paise are said after the rupees ("… रुपये पचहत्तर पैसे"). Everything else is left as it is.
     */
    fun amountsInWords(text: String): String = AMOUNT.replace(text) { m ->
        val unit = m.groupValues[3]
        val bengali = unit == "টাকা"
        val rupees = m.groupValues[1].replace(",", "").toLongOrNull()
        val said = rupees?.let { words(it, bengali) } ?: return@replace m.value
        val paise = m.groupValues[2].takeIf { it.isNotEmpty() }?.padEnd(2, '0')?.toInt() ?: 0
        if (paise == 0) "$said $unit"
        else "$said $unit ${words(paise.toLong(), bengali)} ${if (bengali) "পয়সা" else "पैसे"}"
    }
}
