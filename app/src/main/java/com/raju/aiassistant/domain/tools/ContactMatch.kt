package com.raju.aiassistant.domain.tools

/**
 * Finds a contact the way people say or type its name: "Vodafone 0 1" finds "Vodafone 01",
 * "rahul sharma" finds "Rahul-Sharma", "Sharma Rahul" finds "Rahul Sharma", and a spoken number
 * finds the contact saved with it. Case, spaces and punctuation are ignored; Hindi and Bengali
 * digits count as 0-9.
 */
object ContactMatch {
    /** Lower case, letters and digits only, local digits as 0-9. */
    fun normalize(text: String): String = buildString {
        for (ch in text.lowercase()) {
            val c = localDigit(ch)
            if (c.isLetterOrDigit()) append(c)
        }
    }

    private fun localDigit(ch: Char): Char = when (ch) {
        in '०'..'९' -> '0' + (ch - '०') // Devanagari ०-९
        in '০'..'৯' -> '0' + (ch - '০') // Bengali ০-৯
        else -> ch
    }

    private fun digits(text: String): String = text.map(::localDigit).filter { it in '0'..'9' }.joinToString("")

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
     * it, every word of it in the name, the same sound (Hindi or English spelling), or the same
     * phone number. Empty when nothing matches. The same number saved twice for one name is listed once.
     */
    fun best(query: String, contacts: List<Contact>): List<Contact> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        val named = contacts.map { it to normalize(it.name) }
        val result = named.filter { it.second == q }.ifEmpty { named.filter { it.second.contains(q) } }.ifEmpty {
            val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).map(::normalize).filter { it.isNotEmpty() }
            if (words.size > 1) named.filter { (_, n) -> words.all { n.contains(it) } } else emptyList()
        }.map { it.first }.ifEmpty {
            // Same sound in Hindi or English. Short skeletons ("m" for both Mummy and मम्मी) are too
            // common to trust, so they only match by the exact name above.
            val sk = skeleton(query)
            if (sk.length >= 3) contacts.filter { skeleton(it.name).contains(sk) } else emptyList()
        }.ifEmpty {
            val qd = digits(query)
            if (qd.length >= 6 && qd.length * 2 >= q.length) contacts.filter { digits(it.number).takeLast(10).let { n -> n.length >= 6 && (n.endsWith(qd.takeLast(10)) || qd.endsWith(n)) } } else emptyList()
        }
        return result.distinctBy { normalize(it.name) to digits(it.number).takeLast(10) }
    }
}
