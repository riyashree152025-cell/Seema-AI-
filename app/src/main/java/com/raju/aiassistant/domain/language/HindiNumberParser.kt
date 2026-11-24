package com.raju.aiassistant.domain.language

/**
 * Universal Hindi & Hinglish spoken number parser.
 * Converts natural spoken Hindi/Hinglish numbers and phrases into clean decimal digits:
 * - "सात हज़ार पचास" -> 7050
 * - "सात हज़ार पचासी" -> 7085
 * - "पच्चीस सौ" -> 2500
 * - "डेढ़ हज़ार" -> 1500
 * - "ढाई हज़ार" -> 2500
 * - "साढ़े तीन हज़ार" -> 3500
 * - "दो लाख" -> 200000
 * - "डेढ़ सौ" -> 150
 * - "ढाई सौ" -> 250
 * - "सवा सौ" -> 125
 * - "पौने दो सौ" -> 175
 * - "पाँच सौ" -> 500
 * - "अस्सी" -> 80
 * - "पचास" -> 50
 */
object HindiNumberParser {

    private val DIGITS_MAP: Map<String, Long> = mapOf(
        // 0-9
        "shunya" to 0L, "sifar" to 0L, "शून्य" to 0L, "सिफर" to 0L,
        "ek" to 1L, "एक" to 1L,
        "do" to 2L, "दो" to 2L,
        "teen" to 3L, "तीन" to 3L,
        "char" to 4L, "chaar" to 4L, "चार" to 4L,
        "paanch" to 5L, "panch" to 5L, "पांच" to 5L, "पाँच" to 5L,
        "chhe" to 6L, "chhah" to 6L, "छह" to 6L, "छः" to 6L, "छे" to 6L,
        "saat" to 7L, "सात" to 7L,
        "aath" to 8L, "आठ" to 8L,
        "nau" to 9L, "नौ" to 9L,
        // 10-19
        "das" to 10L, "दस" to 10L,
        "gyarah" to 11L, "ग्यारह" to 11L,
        "barah" to 12L, "बारह" to 12L,
        "terah" to 13L, "तेरह" to 13L,
        "chaudah" to 14L, "चौदह" to 14L,
        "pandrah" to 15L, "पंद्रह" to 15L, "पन्द्रह" to 15L,
        "solah" to 16L, "सोलह" to 16L,
        "satrah" to 17L, "सत्रह" to 17L,
        "atharah" to 18L, "अठारह" to 18L,
        "unnis" to 19L, "उन्नीस" to 19L,
        // 20-29
        "bees" to 20L, "बीस" to 20L,
        "ikkees" to 21L, "ikkis" to 21L, "इक्कीस" to 21L,
        "baees" to 22L, "bais" to 22L, "बाईस" to 22L,
        "teis" to 23L, "तेईस" to 23L,
        "chaubees" to 24L, "chaubis" to 24L, "चौबीस" to 24L,
        "pachchees" to 25L, "pachchis" to 25L, "pacchis" to 25L, "पच्चीस" to 25L,
        "chhabbees" to 26L, "chhabbis" to 26L, "छब्बीस" to 26L,
        "sattaees" to 27L, "sattais" to 27L, "सत्ताईस" to 27L,
        "atthaees" to 28L, "tthais" to 28L, "अट्ठाईस" to 28L, "अठ्ठाईस" to 28L,
        "untees" to 29L, "untis" to 29L, "उनतीस" to 29L,
        // 30-39
        "tees" to 30L, "तीस" to 30L,
        "iktees" to 31L, "iktis" to 31L, "इकतीस" to 31L,
        "battees" to 32L, "battis" to 32L, "बत्तीस" to 32L,
        "taintees" to 33L, "taintis" to 33L, "तैंतीस" to 33L,
        "chauntees" to 34L, "chauntis" to 34L, "चौंतीस" to 34L,
        "paintees" to 35L, "paintis" to 35L, "पैंतीस" to 35L,
        "chhattees" to 36L, "chhattis" to 36L, "छत्तीस" to 36L,
        "saintees" to 37L, "saintis" to 37L, "सैंतीस" to 37L,
        "adhtees" to 38L, "adhtis" to 38L, "अड़तीस" to 38L,
        "untalees" to 39L, "untalis" to 39L, "उनतालीस" to 39L,
        // 40-49
        "chalees" to 40L, "chalis" to 40L, "चालीस" to 40L,
        "iktalees" to 41L, "iktalis" to 41L, "इकतालीस" to 41L,
        "bayalees" to 42L, "bayalis" to 42L, "बयालीस" to 42L,
        "taintalees" to 43L, "taintalis" to 43L, "तैंतालीस" to 43L,
        "chavalees" to 44L, "chavalis" to 44L, "चवालीस" to 44L,
        "paintalees" to 45L, "paintalis" to 45L, "पैंतालीस" to 45L,
        "chhiyalees" to 46L, "chhiyalis" to 46L, "छियालीस" to 46L,
        "saintalees" to 47L, "saintalis" to 47L, "सैंतालीस" to 47L,
        "adhtalees" to 48L, "adhtalis" to 48L, "अड़तालीस" to 48L,
        "unchaas" to 49L, "unchas" to 49L, "उनचास" to 49L,
        // 50-59
        "pachaas" to 50L, "pachas" to 50L, "पचास" to 50L,
        "ikyavan" to 51L, "इक्यावन" to 51L,
        "baavan" to 52L, "बावन" to 52L,
        "tirepan" to 53L, "तिरेपन" to 53L,
        "chauvan" to 54L, "चौवन" to 54L,
        "pachpan" to 55L, "पचपन" to 55L,
        "chhappan" to 56L, "छप्पन" to 56L,
        "sattaavan" to 57L, "सत्तावन" to 57L,
        "atthaavan" to 58L, "अट्ठावन" to 58L,
        "unsath" to 59L, "उनसठ" to 59L,
        // 60-69
        "saath" to 60L, "साठ" to 60L,
        "iksath" to 61L, "इकसठ" to 61L,
        "baasath" to 62L, "बासठ" to 62L,
        "tiresath" to 63L, "तिरेसठ" to 63L,
        "chausath" to 64L, "चौंसठ" to 64L,
        "painsath" to 65L, "पैंसठ" to 65L,
        "chhiyashath" to 66L, "छियासठ" to 66L,
        "sarsath" to 67L, "सरसठ" to 67L,
        "adsath" to 68L, "अड़सठ" to 68L,
        "unhattar" to 69L, "उनहत्तर" to 69L,
        // 70-79
        "sattar" to 70L, "सत्तर" to 70L,
        "ikhattar" to 71L, "इकहत्तर" to 71L,
        "bahattar" to 72L, "बहत्तर" to 72L,
        "tihattar" to 73L, "तिहत्तर" to 73L,
        "chauhattar" to 74L, "चौहत्तर" to 74L,
        "pachhattar" to 75L, "पचहत्तर" to 75L,
        "chhihattar" to 76L, "छिहत्तर" to 76L,
        "sathattar" to 77L, "सतहत्तर" to 77L,
        "athhattar" to 78L, "अठहत्तर" to 78L,
        "unyasi" to 79L, "unasi" to 79L, "उन्नासी" to 79L,
        // 80-89
        "assi" to 80L, "अस्सी" to 80L,
        "ikyasi" to 81L, "इक्यासी" to 81L,
        "bayasi" to 82L, "बयासी" to 82L,
        "tirasi" to 83L, "तिरासी" to 83L,
        "chaurasi" to 84L, "चौरासी" to 84L,
        "pachasi" to 85L, "pichasi" to 85L, "pichyasi" to 85L, "पचासी" to 85L, "पिच्यासी" to 85L, "पिचासी" to 85L,
        "chhiyasi" to 86L, "छियासी" to 86L,
        "satasi" to 87L, "सतासी" to 87L,
        "athasi" to 88L, "अठासी" to 88L,
        "navasi" to 89L, "नवासी" to 89L,
        // 90-99
        "nabbe" to 90L, "नब्बे" to 90L,
        "ikyanve" to 91L, "इक्यानवे" to 91L,
        "baanve" to 92L, "बानवे" to 92L,
        "tiranve" to 93L, "तिरानवे" to 93L,
        "chauranve" to 94L, "चौरानवे" to 94L,
        "pachhanve" to 95L, "पचानवे" to 95L,
        "chhiyanve" to 96L, "छियानवे" to 96L,
        "santanve" to 97L, "सत्तानवे" to 97L,
        "athanve" to 98L, "अट्ठानवे" to 98L,
        "ninyanve" to 99L, "निन्यानवे" to 99L,
    )

    private val MULTIPLIERS = mapOf(
        "sau" to 100L, "so" to 100L, "सौ" to 100L,
        "hazar" to 1000L, "hazaar" to 1000L, "hajar" to 1000L, "हज़ार" to 1000L, "हजार" to 1000L, "k" to 1000L,
        "lakh" to 100000L, "lac" to 100000L, "लाख" to 100000L,
        "crore" to 10000000L, "karod" to 10000000L, "करोड़" to 10000000L,
    )

    /**
     * Parses a single numeric phrase (e.g. "सात हज़ार पचासी", "पच्चीस सौ", "डेढ़ हज़ार") into its numeric value.
     * Returns null if no valid number is recognized.
     */
    fun parseHindiNumber(phrase: String): Long? = parseNumber(phrase)

    fun parseNumber(phrase: String): Long? {
        val trimmed = phrase.trim().lowercase()
        if (trimmed.isEmpty()) return null

        // Check if plain integer or decimal digits
        trimmed.toLongOrNull()?.let { return it }

        // Fractional prefixes:
        // "डेढ़" = 1.5, "ढाई" = 2.5, "साढ़े" = +0.5, "सवा" = 1.25, "पौने" = 0.75
        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        // Special standalone fractional words
        if (tokens.size == 1) {
            when (tokens[0]) {
                "dedh", "डेढ़" -> return 150L // Usually 150 or 1.5, in money context "डेढ़ सौ" is 150
                "dhai", "ढाई" -> return 250L
            }
            DIGITS_MAP[tokens[0]]?.let { return it }
        }

        var total = 0L
        var currentChunk = 0L
        var i = 0

        while (i < tokens.size) {
            val token = tokens[i]

            // 1. Check for fractional words
            if (token == "dedh" || token == "डेढ़") {
                val nextMult = tokens.getOrNull(i + 1)?.let { MULTIPLIERS[it] }
                if (nextMult != null) {
                    total += (nextMult * 1.5).toLong()
                    i += 2
                    continue
                } else {
                    currentChunk += 150L
                    i++
                    continue
                }
            }
            if (token == "dhai" || token == "ढाई") {
                val nextMult = tokens.getOrNull(i + 1)?.let { MULTIPLIERS[it] }
                if (nextMult != null) {
                    total += (nextMult * 2.5).toLong()
                    i += 2
                    continue
                } else {
                    currentChunk += 250L
                    i++
                    continue
                }
            }
            if (token == "sava" || token == "सवा") {
                val nextMult = tokens.getOrNull(i + 1)?.let { MULTIPLIERS[it] }
                if (nextMult != null) {
                    total += (nextMult * 1.25).toLong()
                    i += 2
                    continue
                }
                val nextVal = tokens.getOrNull(i + 1)?.let { DIGITS_MAP[it] ?: it.toLongOrNull() }
                val nextNextMult = tokens.getOrNull(i + 2)?.let { MULTIPLIERS[it] }
                if (nextVal != null && nextNextMult != null) {
                    total += ((nextVal + 0.25) * nextNextMult).toLong()
                    i += 3
                    continue
                }
            }
            if (token == "paune" || token == "पौने") {
                val nextToken = tokens.getOrNull(i + 1)
                val nextMult = nextToken?.let { MULTIPLIERS[it] }
                if (nextMult != null) {
                    total += (nextMult * 0.75).toLong()
                    i += 2
                    continue
                }
                val nextVal = nextToken?.let { DIGITS_MAP[it] ?: it.toLongOrNull() }
                val nextNextMult = tokens.getOrNull(i + 2)?.let { MULTIPLIERS[it] }
                if (nextVal != null && nextNextMult != null) {
                    total += ((nextVal - 0.25) * nextNextMult).toLong()
                    i += 3
                    continue
                }
            }
            if (token == "sadhe" || token == "साढ़े") {
                val nextVal = tokens.getOrNull(i + 1)?.let { DIGITS_MAP[it] ?: it.toLongOrNull() }
                val nextMult = tokens.getOrNull(i + 2)?.let { MULTIPLIERS[it] }
                if (nextVal != null && nextMult != null) {
                    total += ((nextVal + 0.5) * nextMult).toLong()
                    i += 3
                    continue
                }
            }

            // 2. Check for digits / digits map / literal numbers
            val numVal = DIGITS_MAP[token] ?: token.toLongOrNull()
            val multVal = MULTIPLIERS[token]

            when {
                multVal != null -> {
                    val factor = if (currentChunk == 0L) 1L else currentChunk
                    total += factor * multVal
                    currentChunk = 0L
                }
                numVal != null -> {
                    currentChunk += numVal
                }
                else -> {
                    // Unknown word inside number phrase; if we have accumulated something, stop
                    if (total > 0 || currentChunk > 0) break else return null
                }
            }
            i++
        }

        val result = total + currentChunk
        return result.takeIf { it > 0 }
    }

    private val VERB_BEFORE_DO = setOf(
        "hata", "kar", "karo", "rok", "chala", "de", "diye", "diya", "bata", "bhej", "likh", "khol", "band",
        "हटा", "कर", "करो", "रोक", "चला", "दे", "दिए", "दिया", "बता", "भेज", "लिख", "खोल", "बंद",
    )

    /**
     * Finds and replaces spoken Hindi number sequences in [text] with plain ASCII digits.
     * E.g. "राहुल से सात हज़ार पचास रुपये लेने बाकी हैं" -> "राहुल से 7050 रुपये लेने बाकी हैं"
     */
    fun replaceHindiNumbers(text: String): String {
        val tokens = text.split(Regex("(?<=[\\s,])|(?=[\\s,])"))
        val result = StringBuilder()
        var i = 0

        fun isNumToken(index: Int): Boolean {
            val s = tokens[index].trim().lowercase()
            if (s.isEmpty()) return false
            if (s in setOf("do", "दो")) {
                var prev: String? = null
                for (k in index - 1 downTo 0) {
                    if (tokens[k].isNotBlank()) { prev = tokens[k].trim().lowercase(); break }
                }
                if (prev in VERB_BEFORE_DO) return false

                var next: String? = null
                for (k in index + 1 until tokens.size) {
                    if (tokens[k].isNotBlank()) { next = tokens[k].trim().lowercase(); break }
                }
                if (next == null) return false
                if (next !in MULTIPLIERS && next !in setOf("hazar", "हज़ार", "sau", "सौ", "lakh", "लाख", "crore", "करोड़", "rupaye", "रुपये", "rupay", "baje", "बजे", "log", "लोग")) {
                    return false
                }
            }
            return DIGITS_MAP.containsKey(s) || MULTIPLIERS.containsKey(s) ||
                s in setOf("dedh", "dhai", "sadhe", "sava", "paune", "डेढ़", "ढाई", "साढ़े", "सवा", "पौने") ||
                s.all { it.isDigit() }
        }

        while (i < tokens.size) {
            if (isNumToken(i)) {
                val phraseTokens = mutableListOf<String>()
                phraseTokens.add(tokens[i])
                var j = i + 1
                var lastNumTokenIndex = i
                while (j < tokens.size) {
                    if (tokens[j].isBlank()) {
                        j++
                    } else if (isNumToken(j)) {
                        phraseTokens.add(tokens[j])
                        lastNumTokenIndex = j
                        j++
                    } else {
                        break
                    }
                }
                val phrase = phraseTokens.joinToString(" ")
                val parsed = parseNumber(phrase)
                if (parsed != null) {
                    result.append(parsed)
                    i = lastNumTokenIndex + 1
                    continue
                }
            }
            result.append(tokens[i])
            i++
        }

        return result.toString()
    }
}
