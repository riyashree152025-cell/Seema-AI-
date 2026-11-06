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
        val found = result.ifEmpty { rank(query, contacts).filter { it.score >= FUZZY_MIN }.map { it.contact } }
        return found.distinctBy { normalize(it.name) to digits(it.number).takeLast(10) }
    }

    // ------------------------------------------------------------------ names said differently

    /** A contact and how well its name matches what was said (0..1). */
    data class Scored(val contact: Contact, val score: Double)

    /** Lowest score that counts as "probably this one". */
    const val FUZZY_MIN = 0.72

    /** A score this high, clearly ahead of the next name, is treated as the one meant. */
    const val SURE = 0.86

    /**
     * Every contact scored against [query], best first, at most [limit]: "रिजवान Travels" finds
     * "Rizwan Travels", "rizvan travel" too, and "राहुल" finds "Rahul Sharma". The same number saved
     * twice under one name is listed once.
     */
    fun rank(query: String, contacts: List<Contact>, limit: Int = 8): List<Scored> {
        val q = words(query)
        if (q.isEmpty()) return emptyList()
        val qJoined = q.joinToString("") { it.full }
        return contacts.asSequence()
            .map { c -> Scored(c, score(q, qJoined, c.name)) }
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
            .distinctBy { normalize(it.contact.name) to digits(it.contact.number).takeLast(10) }
            .take(limit)
            .toList()
    }

    /** True when [ranked] (best first) has one clear winner. */
    fun isClearWinner(ranked: List<Scored>): Boolean {
        val first = ranked.firstOrNull() ?: return false
        val second = ranked.getOrNull(1)?.score ?: 0.0
        return first.score >= SURE && first.score - second >= 0.08
    }

    private class Word(val full: String, val skeleton: String)

    private fun words(text: String): List<Word> =
        com.raju.aiassistant.domain.language.Transliterate.toLatin(text).lowercase()
            .map { if (it.isLetterOrDigit()) localDigit(it) else ' ' }.joinToString("")
            .split(' ').filter { it.isNotBlank() }
            .map { w -> sound(w).let { Word(it, skeleton(it)) } }
            .filter { it.full.isNotEmpty() }

    /**
     * A spelling of how a word sounds, so Hindi and English spellings meet: "rizwan" and "rijavan"
     * both become "rijavan"-like, "travels" and "traivals" both "travels". Vowels stay (shortened).
     */
    internal fun sound(word: String): String {
        var w = plain(word)
        w = w.replace("chh", "C").replace("ch", "C").replace(Regex("c(?=[eiy])"), "s").replace('c', 'k').replace('C', 'c')
        w = w.replace("ph", "f").replace("kh", "k").replace("gh", "g").replace("jh", "j").replace("th", "t")
            .replace("dh", "d").replace("bh", "b").replace("sh", "s").replace("ck", "k")
            .replace('q', 'k').replace('z', 'j').replace('w', 'v').replace("x", "ks")
        w = w.replace("ee", "i").replace("oo", "u").replace("ai", "e").replace("ay", "e").replace("au", "o").replace("ou", "o")
        // Double letters as one: "aa" -> "a", "ll" -> "l".
        return buildString { for (ch in w) if (isEmpty() || last() != ch) append(ch) }
    }

    /** Consonants only (the first letter kept): survives the vowels each script spells differently. */
    private fun skeleton(sound: String): String =
        if (sound.length <= 1) sound else sound.first() + sound.drop(1).filter { it !in "aeiouy" }

    private fun plain(word: String): String = buildString {
        for (ch in word) append(if (ch in '\u00C0'..'\u024F') java.text.Normalizer.normalize(ch.toString(), java.text.Normalizer.Form.NFD).first() else ch)
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxOf(a.length, b.length)
    }

    private fun wordSimilarity(a: Word, b: Word): Double {
        // Digits must match exactly ("Vodafone 01" is not "Vodafone 02").
        if (a.full.any { it.isDigit() } || b.full.any { it.isDigit() }) return if (a.full == b.full) 1.0 else 0.0
        val sk = similarity(a.skeleton, b.skeleton)
        val full = similarity(a.full, b.full)
        // A said word that starts the saved word ("rizwan" in "rizwanbhai") counts nearly fully.
        val prefix = if (a.full.length >= 3 && b.full.startsWith(a.full)) 0.92 else 0.0
        return maxOf(0.55 * sk + 0.45 * full, prefix)
    }

    private fun score(q: List<Word>, qJoined: String, name: String): Double {
        val c = words(name)
        if (c.isEmpty()) return 0.0
        val perWord = q.map { qw -> c.maxOf { wordSimilarity(qw, it) } }
        // A weak word drags the whole name down: "Rizwan Travels" is not "Rizwan Khan".
        var s = perWord.average() - 0.15 * perWord.count { it < 0.6 }
        // Saved name has more words than were said: a little lower, so the shorter, exact name wins.
        if (c.size > q.size) s -= 0.03 * (c.size - q.size)
        val joined = similarity(qJoined, c.joinToString("") { it.full }) - 0.02
        return maxOf(s, joined, 0.0)
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
