package com.raju.aiassistant.domain.rail

/**
 * Turns what speech recognition wrote for a PNR (10 digits) or a train number (5 digits) into the
 * digits RailKit needs: "6211 9126 38", "6211-9126-38", "६२११९१२६३८", "double one", "triple zero",
 * "ek do teen", "एक दो तीन" all become plain digits. Anything else is rejected, so a wrong number is
 * never sent to the railway service (which answers 400 Bad Request).
 */
object VoiceNumberSanitizer {
    /** Spoken digit words (English, Hindi, Bengali spellings used by the recogniser). Lower case. */
    private val DIGIT_WORDS: Map<String, Char> = mapOf(
        "zero" to '0', "oh" to '0', "shunya" to '0', "sifar" to '0', "शून्य" to '0', "सिफर" to '0', "শূন্য" to '0',
        "one" to '1', "ek" to '1', "एक" to '1', "এক" to '1',
        "two" to '2', "do" to '2', "दो" to '2', "দুই" to '2',
        "three" to '3', "teen" to '3', "tin" to '3', "तीन" to '3', "তিন" to '3',
        "four" to '4', "char" to '4', "चार" to '4', "চার" to '4',
        "five" to '5', "paanch" to '5', "panch" to '5', "पांच" to '5', "पाँच" to '5', "পাঁচ" to '5',
        "six" to '6', "chhe" to '6', "chhah" to '6', "छह" to '6', "छः" to '6', "ছয়" to '6',
        "seven" to '7', "saat" to '7', "सात" to '7', "সাত" to '7',
        "eight" to '8', "aath" to '8', "आठ" to '8', "আট" to '8',
        "nine" to '9', "nau" to '9', "नौ" to '9', "নয়" to '9',
    )
    private val REPEAT = mapOf("double" to 2, "dabal" to 2, "डबल" to 2, "ডাবল" to 2, "triple" to 3, "tripal" to 3, "ट्रिपल" to 3, "ট্রিপল" to 3)

    /** Every spoken or written digit form as ASCII digits, in order. Other characters are dropped. */
    fun digits(spoken: String): String {
        val tokens = spoken.lowercase().split(Regex("[\\s,\\-–—/.]+")).filter { it.isNotEmpty() }
        val out = StringBuilder()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val repeat = REPEAT[t]
            if (repeat != null && i + 1 < tokens.size) {
                // "double one" → 11, "triple zero" → 000; the next token must itself be a digit form.
                val next = tokens[i + 1]
                val d = digitOf(next)
                if (d != null) {
                    repeat(repeat) { out.append(d) }
                    i += 2
                    continue
                }
            }
            val wordDigit = DIGIT_WORDS[t]
            if (wordDigit != null) out.append(wordDigit)
            else for (ch in t) {
                // Devanagari digits (०–९) and plain ASCII digits; any other character is ignored.
                val mapped = when (ch) {
                    in '०'..'९' -> '0' + (ch - '०')
                    in '0'..'9' -> ch
                    else -> null
                }
                if (mapped != null) out.append(mapped)
            }
            i++
        }
        return out.toString()
    }

    private fun digitOf(token: String): Char? = DIGIT_WORDS[token] ?: token.singleOrNull()?.takeIf { it in '0'..'9' || it in '०'..'९' }?.let {
        if (it in '०'..'९') '0' + (it - '०') else it
    }

    /** The 10-digit PNR, or null with the count of digits heard (for the spoken error). */
    fun pnr(spoken: String): Result<String> = exactly(spoken, 10, "PNR")

    /** The 5-digit train number, or null with the count of digits heard. */
    fun trainNumber(spoken: String): Result<String> = exactly(spoken, 5, "train number")

    private fun exactly(spoken: String, length: Int, what: String): Result<String> {
        val d = digits(spoken)
        return if (d.length == length) Result.success(d)
        else Result.failure(IllegalArgumentException("$what $length अंकों का होना चाहिए, मुझे ${d.length} अंक सुनाई दिए।"))
    }
}
