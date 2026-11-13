package com.raju.aiassistant.domain.language

/**
 * Hindi speech recognition writes numbers as words ("उन्नीस सौ छियासी"). This turns them into digits
 * ("1986") so a birth year, an age or a count can be read: 1–99, "X सौ Y" (hundreds, so years like
 * 1986) and "दो हज़ार Y" (2000 + Y). A repeated number word straight after "सौ" (a recogniser stumble,
 * "उन्नीस सौ छह छियासी") keeps the last one. Other words are left as they are.
 */
private val UNITS = listOf(
    "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ", "दस",
    "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस", "बीस",
    "इक्कीस", "बाईस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस", "तीस",
    "इकतीस", "बत्तीस", "तैंतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस", "चालीस",
    "इकतालीस", "बयालीस", "तैंतालीस", "चवालीस", "पैंतालीस", "छियालीस", "सैंतालीस", "अड़तालीस", "उनचास", "पचास",
    "इक्यावन", "बावन", "तिरेपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ", "साठ",
    "इकसठ", "बासठ", "तिरसठ", "चौंसठ", "पैंसठ", "छियासठ", "सड़सठ", "अड़सठ", "उनहत्तर", "सत्तर",
    "इकहत्तर", "बहत्तर", "तिहत्तर", "चौहत्तर", "पचहत्तर", "छिहत्तर", "सतहत्तर", "अठहत्तर", "उन्यासी", "अस्सी",
    "इक्यासी", "बयासी", "तिरासी", "चौरासी", "पचासी", "छियासी", "सत्तासी", "अट्ठासी", "नवासी", "नब्बे",
    "इक्यानवे", "बानवे", "तिरानवे", "चौरानवे", "पंचानवे", "छियानवे", "सत्तानवे", "अट्ठानवे", "निन्यानवे",
)

private val NUMBER: Map<String, Int> = UNITS.withIndex().associate { (i, w) -> w to i + 1 } + mapOf(
    "पांच" to 5, "छः" to 6, "छे" to 6, "छै" to 6, "पन्द्रह" to 15, "अठाईस" to 28, "उन्नासी" to 79, "नवासि" to 89,
)
private val HUNDRED = setOf("सौ", "सो")
private val THOUSAND = setOf("हज़ार", "हजार")

fun hindiNumbersToDigits(text: String): String {
    val words = text.split(Regex("\\s+"))
    val out = mutableListOf<String>()
    var i = 0
    fun num(k: Int) = words.getOrNull(k)?.trimEnd(',', '.', '।', '?', '!')?.let { NUMBER[it] }
    while (i < words.size) {
        val a = num(i)
        if (a == null) { out += words[i]; i++; continue }
        val next = words.getOrNull(i + 1)?.trimEnd(',', '.', '।')
        when {
            next in HUNDRED -> {
                var j = i + 2
                var rest = 0
                while (num(j) != null) { rest = num(j)!!; j++ } // the last number word after "सौ"
                out += (a * 100 + rest).toString() + trailing(words[j - 1])
                i = j
            }
            next in THOUSAND -> {
                var j = i + 2
                var rest = 0
                while (num(j) != null) { rest = num(j)!!; j++ }
                out += (a * 1000 + rest).toString() + trailing(words[j - 1])
                i = j
            }
            // A lone small word stays a word: "दो" is also "give" (kar do), "नौ" / "सात" read as words too.
            a < 11 -> { out += words[i]; i++ }
            else -> { out += a.toString() + trailing(words[i]); i++ }
        }
    }
    return out.joinToString(" ")
}

/** Punctuation that followed the last word of a number ("छियासी," → ","). */
private fun trailing(w: String): String = w.takeLastWhile { it in ",.।?!" }
