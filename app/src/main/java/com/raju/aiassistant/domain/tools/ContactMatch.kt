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
     * The contacts that best match [query], best kind of match first: same name, name containing
     * it, every word of it in the name, or the same phone number. Empty when nothing matches.
     * The same number saved twice for one name is listed once.
     */
    fun best(query: String, contacts: List<Contact>): List<Contact> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        val named = contacts.map { it to normalize(it.name) }
        val result = named.filter { it.second == q }.ifEmpty { named.filter { it.second.contains(q) } }.ifEmpty {
            val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).map(::normalize).filter { it.isNotEmpty() }
            if (words.size > 1) named.filter { (_, n) -> words.all { n.contains(it) } } else emptyList()
        }.map { it.first }.ifEmpty {
            val qd = digits(query)
            if (qd.length >= 6 && qd.length * 2 >= q.length) contacts.filter { digits(it.number).takeLast(10).let { n -> n.length >= 6 && (n.endsWith(qd.takeLast(10)) || qd.endsWith(n)) } } else emptyList()
        }
        return result.distinctBy { normalize(it.name) to digits(it.number).takeLast(10) }
    }
}
