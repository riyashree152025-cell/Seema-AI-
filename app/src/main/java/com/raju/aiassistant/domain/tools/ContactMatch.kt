package com.raju.aiassistant.domain.tools

/**
 * Finds a contact the way people say or type its name: "Vodafone 0 1" finds "Vodafone 01",
 * "rahul sharma" finds "Rahul-Sharma", "Sharma Rahul" finds "Rahul Sharma", and a spoken number
 * finds the contact saved with it. Case, spaces and punctuation are ignored; Hindi and Bengali
 * digits count as 0-9.
 */
object ContactMatch {
    /** A word as people say it: letters, marks (Devanagari matras) and digits. */
    private val WORD = Regex("[\\p{L}\\p{M}\\p{N}]+")
    private val TOKEN_SEP = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    /** Spoken numbers that mean a digit: "One", "वन", "एक" → "1". Only words that are not common names. */
    private val NUMBER_WORDS: Map<String, String> = mapOf(
        "zero" to "0", "one" to "1", "ek" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10",
        "शून्य" to "0", "वन" to "1", "एक" to "1", "दो" to "2", "तीन" to "3", "चार" to "4",
        "पांच" to "5", "पाँच" to "5", "छह" to "6", "छः" to "6", "छे" to "6", "सात" to "7",
        "आठ" to "8", "नौ" to "9", "दस" to "10",
    )

    /** Words said before or after a name that do not belong to the saved name ("Jalal Bhai" is "Jalal"). */
    private val HONORIFICS: Set<String> = setOf(
        "bhai", "bhaiya", "bhayya", "bhaiyya", "ji", "sahab", "saheb", "sir", "madam", "didi",
        "uncle", "aunty", "aunti", "bhabhi", "bhabi", "भाई", "भैया", "जी", "साहब", "दीदी", "अंकल", "आंटी", "भाभी",
    )

    /** Same as [withDigitsFromWords] for every word that is a number word. */
    fun withDigitsFromWords(text: String): String =
        WORD.replace(text) { m -> NUMBER_WORDS[m.value.lowercase()] ?: m.value }

    /** Query without honorifics: "Jalal Bhai" → "Jalal". Empty when nothing else is left. */
    fun stripHonorifics(text: String): String =
        WORD.findAll(text).map { it.value }.filter { it.lowercase() !in HONORIFICS }.joinToString(" ")

    /**
     * Lower case, letters and digits only, local digits as 0-9, number words as digits, and leading
     * zeros dropped from every number: "Vodafone 01", "Vodafone 1", "Vodafone One" and "वोडाफोन वन"
     * all become "vodafone1".
     */
    fun normalize(text: String): String {
        val raw = buildString {
            for (ch in withDigitsFromWords(text).lowercase()) {
                val c = localDigit(ch)
                if (c.isLetterOrDigit()) append(c)
            }
        }
        return raw.replace(Regex("[0-9]+")) { it.value.trimStart('0').ifEmpty { "0" } }
    }

    private fun localDigit(ch: Char): Char = when (ch) {
        in '०'..'९' -> '0' + (ch - '०') // Devanagari ०-९
        in '০'..'৯' -> '0' + (ch - '০') // Bengali ০-৯
        else -> ch
    }

    private fun digits(text: String): String = text.map(::localDigit).filter { it in '0'..'9' }.joinToString("")

    /** The digits of a name or query without leading zeros: "01" and "1" are the same number. */
    private fun canonDigits(text: String): String = digits(text).trimStart('0')

    /**
     * A rough sound-skeleton of a name, the same for Hindi and English spellings: consonants only,
     * sounds that are alike folded together (ज/ज़ and z, व and w, क and c/k, फ and f), repeats
     * collapsed. "रिजवान ट्रैवल्स" and "Rizwan Travels" both become "rjvntrvls".
     */
    fun skeleton(text: String): String {
        val out = StringBuilder()
        for (ch in text.replace("\u093c", "")) {
            val mapped = DEVANAGARI[ch]
            when {
                mapped != null -> out.append(mapped)
                ch == '\u0902' || ch == '\u0901' -> out.append('n') // anusvara, chandrabindu
                ch in '\u0900'..'\u097f' -> Unit // vowel signs, halant, visarga: no consonant
                ch.isLetter() -> out.append(ch.lowercaseChar())
            }
        }
        var s = out.toString().replace("sh", "s").replace("ph", "p").replace("ch", "c").replace("wh", "v")
        s = buildString { for (c in s) append(LATIN_FOLD[c] ?: c) }
        s = s.filter { it !in "aeiouhy" }
        return buildString {
            for (c in s) if (isEmpty() || last() != c) append(c)
        }
    }

    private val LATIN_FOLD = mapOf('z' to 'j', 'w' to 'v', 'q' to 'k', 'c' to 'k', 'f' to 'p', 'x' to 'k')

    private val DEVANAGARI: Map<Char, Char> = mapOf(
        'क' to 'k', 'ख' to 'k', 'ग' to 'g', 'घ' to 'g', 'ङ' to 'n', 'च' to 'c', 'छ' to 'c', 'ज' to 'j', 'झ' to 'j', 'ञ' to 'n',
        'ट' to 't', 'ठ' to 't', 'ड' to 'd', 'ढ' to 'd', 'ण' to 'n', 'त' to 't', 'थ' to 't', 'द' to 'd', 'ध' to 'd', 'न' to 'n',
        'प' to 'p', 'फ' to 'p', 'ब' to 'b', 'भ' to 'b', 'म' to 'm', 'य' to 'y', 'र' to 'r', 'ल' to 'l', 'व' to 'v',
        'श' to 's', 'ष' to 's', 'स' to 's', 'ह' to 'h',
    )

    /**
     * The contacts that best match [query], best kind of match first: same name, name containing
     * it, every word of it in the name, the same sound (Hindi or English spelling), the same
     * phone number, or a name close in spelling ("Rizwen Travls" → "Rizwan Travels"). Empty when
     * nothing matches. The same number saved twice for one name is listed once.
     *
     * The query is tried as spoken first ("Jalal Bhai" finds a contact saved as "Jalal Bhai"), then
     * without honorifics ("Jalal Bhai" finds "Jalal").
     */
    fun best(query: String, contacts: List<Contact>): List<Contact> {
        val spoken = withDigitsFromWords(query)
        val core = stripHonorifics(spoken).ifBlank { spoken }
        for (q in listOf(spoken, core).distinct()) {
            val found = matchNames(q, contacts, spelling = true)
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    /**
     * True when [query] found [contacts] only by a guess at the spelling ("Ramesh" → "Rakesh"), not by the
     * name, its sound or its number. Such a match must be confirmed before anything is dialled.
     */
    fun isSpellingGuess(query: String, contacts: List<Contact>): Boolean {
        val spoken = withDigitsFromWords(query)
        val core = stripHonorifics(spoken).ifBlank { spoken }
        if (listOf(spoken, core).distinct().any { matchNames(it, contacts, spelling = false).isNotEmpty() }) return false
        return best(query, contacts).isNotEmpty()
    }

    private fun matchNames(query: String, contacts: List<Contact>, spelling: Boolean): List<Contact> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        val named = contacts.map { it to normalize(it.name) }
        val result = named.filter { it.second == q }.ifEmpty { named.filter { it.second.contains(q) } }.ifEmpty {
            val words = query.lowercase().split(TOKEN_SEP).map(::normalize).filter { it.isNotEmpty() }
            if (words.size > 1) named.filter { (_, n) -> words.all { n.contains(it) } } else emptyList()
        }.map { it.first }.ifEmpty {
            // Same sound in Hindi or English. Short skeletons ("m" for both Mummy and मम्मी) are too
            // common to trust, so they only match by the exact name above.
            // Digits are not part of a skeleton, so "Airtel 01" must also have its "01" in the name —
            // otherwise it sounded like "airtel money *400#" and that service code was offered to call.
            val sk = skeleton(query)
            val qd = digits(query)
            if (sk.length >= 3) contacts.filter { skeleton(it.name).contains(sk) && (qd.isEmpty() || digits(it.name).contains(qd)) } else emptyList()
        }.ifEmpty {
            val qd = digits(query)
            if (qd.length >= 6 && qd.length * 2 >= q.length) contacts.filter { digits(it.number).takeLast(10).let { n -> n.length >= 6 && (n.endsWith(qd.takeLast(10)) || qd.endsWith(n)) } } else emptyList()
        }.ifEmpty {
            if (spelling) similarNames(stripHonorifics(query).ifBlank { query }, contacts) else emptyList()
        }
        return result.distinctBy { normalize(it.name) to digits(it.number).takeLast(10) }
    }

    /**
     * Spelling close to a saved name: a word of at least [FUZZY_MIN_LETTERS] letters within
     * [FUZZY_THRESHOLD] (Jaro-Winkler) of a word of the name. Short words are not used: "Riya" scores
     * 0.78 against "Rizwan", and a wrong call is worse than no match. A number in the query must be in the name too, so "Airtel 01" never
     * finds "airtel money". Only used when no name matched in any other way.
     */
    private fun similarNames(query: String, contacts: List<Contact>): List<Contact> {
        val words = query.split(TOKEN_SEP).filter { w -> w.any { it.isLetter() } }.map(::normalize).filter { it.length >= FUZZY_MIN_LETTERS }
        if (words.isEmpty()) return emptyList()
        val qd = canonDigits(query)
        return contacts.filter { c ->
            (qd.isEmpty() || canonDigits(c.name).contains(qd)) && run {
                val nameWords = c.name.split(TOKEN_SEP).map(::normalize).filter { it.length >= FUZZY_MIN_LETTERS }
                words.any { w -> nameWords.any { n -> similarity(w, n) >= FUZZY_THRESHOLD } }
            }
        }
    }

    /** How close two words are, 0..1 (Jaro-Winkler: a shared start counts more). */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val window = maxOf(0, maxOf(a.length, b.length) / 2 - 1)
        val aMatched = BooleanArray(a.length)
        val bMatched = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            val from = maxOf(0, i - window)
            val to = minOf(i + window, b.length - 1)
            for (j in from..to) {
                if (!bMatched[j] && a[i] == b[j]) {
                    aMatched[i] = true; bMatched[j] = true; matches++
                    break
                }
            }
        }
        if (matches == 0) return 0.0
        var transpositions = 0
        var j = 0
        for (i in a.indices) {
            if (!aMatched[i]) continue
            while (!bMatched[j]) j++
            if (a[i] != b[j]) transpositions++
            j++
        }
        val m = matches.toDouble()
        val jaro = (m / a.length + m / b.length + (m - transpositions / 2.0) / m) / 3.0
        var prefix = 0
        while (prefix < minOf(4, a.length, b.length) && a[prefix] == b[prefix]) prefix++
        return jaro + prefix * 0.1 * (1 - jaro)
    }

    /** Jaro-Winkler at or above this counts as the same name spelt differently. */
    const val FUZZY_THRESHOLD = 0.72
    const val FUZZY_MIN_LETTERS = 5

    private val ORDINALS: List<Regex> = listOf(
        "1|pehla|pahla|pehle|pahle|pehli|first|पहला|पहले|पहली|প্রথম|প্রথমটা",
        "2|doosra|dusra|doosre|dusre|doosri|second|दूसरा|दूसरे|दूसरी|দ্বিতীয়|দ্বিতীয়টা",
        "3|teesra|tisra|teesre|teesri|third|तीसरा|तीसरे|तीसरी|তৃতীয়",
        "4|chautha|chauthe|chauthi|fourth|चौथा|चौथे|चौथी|চতুর্থ",
    ).map { Regex("(^|\\s)(?:$it)(\\s|$)", RegexOption.IGNORE_CASE) }

    /**
     * Which of the offered [candidates] the user means by a spoken answer: a name ("राहुल शर्मा"),
     * a number, or a position ("पहला", "doosra", "2"). Null when it is not clear — then she asks again.
     */
    fun pick(heard: String, candidates: List<Contact>): Contact? {
        if (candidates.isEmpty()) return null
        val t = heard.trim().trimEnd('.', '!', '?', '।').trim()
        if (t.isEmpty()) return null
        if (t.split(Regex("\\s+")).size <= 3) {
            ORDINALS.forEachIndexed { i, r -> if (r.containsMatchIn(t) && i < candidates.size) return candidates[i] }
        }
        // "6087" / "chhe zero aath saat" as digits: the one number that ends with (or contains) them.
        val digits = t.map { ch -> if (ch in '०'..'९') '0' + (ch - '०') else ch }.filter { it.isDigit() }.joinToString("")
        if (digits.length >= 3) {
            val byEnd = candidates.filter { it.number.filter(Char::isDigit).endsWith(digits) }
            if (byEnd.size == 1) return byEnd[0]
            val byPart = candidates.filter { it.number.filter(Char::isDigit).contains(digits) }
            if (byPart.size == 1) return byPart[0]
        }
        val found = best(t, candidates)
        return found.singleOrNull()
            ?: found.filter { normalize(it.name) == normalize(t) }.singleOrNull()
    }

    /**
     * How a choice is read out: "1. Riya, 2. Rahul" — or, when the names are the same ("Firoz" twice),
     * with each number's last 4 digits: "pehla Firoz, 6087 wala; doosra Firoz, 5150 wala".
     */
    fun spokenChoices(candidates: List<Contact>, hindi: Boolean = true): String {
        val sameNames = candidates.map { normalize(it.name) }.distinct().size < candidates.size
        val ordinals = if (hindi) listOf("पहला", "दूसरा", "तीसरा", "चौथा") else listOf("প্রথম", "দ্বিতীয়", "তৃতীয়", "চতুর্থ")
        return candidates.take(4).mapIndexed { i, c ->
            if (!sameNames) "${i + 1}. ${c.name}"
            else {
                val last4 = c.number.filter(Char::isDigit).takeLast(4).toList().joinToString(" ")
                if (hindi) "${ordinals[i]} ${c.name}, $last4 वाला" else "${ordinals[i]} ${c.name}, $last4"
            }
        }.joinToString("; ")
    }
}

/** Phone numbers Seema may call or message: full numbers only, never "01", "121" or "*400#". */
object PhoneNumbers {
    fun isCallable(number: String): Boolean {
        if (number.any { it == '*' || it == '#' }) return false
        val d = number.map { ch ->
            when (ch) {
                in '०'..'९' -> '0' + (ch - '०')
                in '০'..'৯' -> '0' + (ch - '০')
                else -> ch
            }
        }.filter { it in '0'..'9' }
        return d.size in 10..15
    }
}
