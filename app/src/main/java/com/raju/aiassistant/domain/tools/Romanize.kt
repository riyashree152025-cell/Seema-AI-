package com.raju.aiassistant.domain.tools

/**
 * Hindi (Devanagari) and Bengali names in Latin letters, with their vowels, the way people write them in
 * their contacts: "रिया" → "riya", "झंडू बाम" → "jhandu baam", "छाया" → "chhaaya", "রিয়া" → "riya".
 * [phonetic] then folds spellings that sound the same ("chhaaya" = "Chhaya", "jhandu" = "Zandu"), so a
 * name said in Hindi finds the same name saved in English — and the other way round.
 */
object Romanize {
    private data class Script(
        val consonants: Map<Char, String>,
        val vowels: Map<Char, String>,
        val signs: Map<Char, String>,
        val halant: Char,
        val nukta: Char?,
        val nasal: Set<Char>,
        val visarga: Char,
    )

    private val DEVANAGARI = Script(
        consonants = mapOf(
            'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
            'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
            'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
            'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h", 'ळ' to "l",
            // Letters with a nukta written as one character.
            '\u0958' to "q", '\u0959' to "kh", '\u095A' to "g", '\u095B' to "z", '\u095C' to "r", '\u095D' to "rh", '\u095E' to "f", '\u095F' to "y",
        ),
        vowels = mapOf(
            'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u", 'ए' to "e", 'ऐ' to "ai",
            'ओ' to "o", 'औ' to "au", 'ऋ' to "ri", 'ऑ' to "o", 'ऍ' to "e",
        ),
        signs = mapOf(
            'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
            'ृ' to "ri", 'ॅ' to "e", 'ॉ' to "o",
        ),
        halant = '्',
        nukta = '़',
        nasal = setOf('ं', 'ँ'),
        visarga = 'ः',
    )

    private val BENGALI = Script(
        consonants = mapOf(
            'ক' to "k", 'খ' to "kh", 'গ' to "g", 'ঘ' to "gh", 'ঙ' to "n", 'চ' to "ch", 'ছ' to "chh", 'জ' to "j", 'ঝ' to "jh", 'ঞ' to "n",
            'ট' to "t", 'ঠ' to "th", 'ড' to "d", 'ঢ' to "dh", 'ণ' to "n", 'ত' to "t", 'থ' to "th", 'দ' to "d", 'ধ' to "dh", 'ন' to "n",
            'প' to "p", 'ফ' to "ph", 'ব' to "b", 'ভ' to "bh", 'ম' to "m", 'য' to "j", 'র' to "r", 'ল' to "l",
            'শ' to "sh", 'ষ' to "sh", 'স' to "s", 'হ' to "h", '\u09DC' to "r", '\u09DD' to "rh", '\u09DF' to "y", 'ৎ' to "t",
        ),
        vowels = mapOf(
            'অ' to "a", 'আ' to "a", 'ই' to "i", 'ঈ' to "i", 'উ' to "u", 'ঊ' to "u", 'এ' to "e", 'ঐ' to "oi",
            'ও' to "o", 'ঔ' to "ou", 'ঋ' to "ri",
        ),
        signs = mapOf(
            'া' to "a", 'ি' to "i", 'ী' to "i", 'ু' to "u", 'ূ' to "u", 'ে' to "e", 'ৈ' to "oi", 'ো' to "o", 'ৌ' to "ou", 'ৃ' to "ri",
        ),
        halant = '্',
        nukta = '়',
        nasal = setOf('ং', 'ঁ'),
        visarga = 'ঃ',
    )

    /** True when [text] has Hindi or Bengali letters. */
    fun isIndic(text: String): Boolean = text.any { it in 'ऀ'..'ॿ' || it in 'ঀ'..'৿' }

    /** Hindi and Bengali letters in Latin letters (vowels kept, a word's final silent "a" dropped). Latin text is kept. */
    fun latin(text: String): String {
        // Letters written with a separate nukta (ज + ़) are joined first, so they read as one letter.
        val src = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
        val out = StringBuilder()
        var i = 0
        while (i < src.length) {
            val ch = src[i]
            val script = when (ch) {
                in 'ऀ'..'ॿ' -> DEVANAGARI
                in 'ঀ'..'৿' -> BENGALI
                else -> null
            }
            if (script == null) {
                out.append(ch)
                i++
                continue
            }
            var cons = script.consonants[ch]
            if (cons != null) {
                var j = i + 1
                if (j < src.length && src[j] == script.nukta) {
                    cons = nuktaForm(ch) ?: cons
                    j++
                }
                out.append(cons)
                val next = src.getOrNull(j)
                when {
                    next == script.halant -> j++ // no vowel: a cluster ("प्रि" = pri)
                    next != null && script.signs.containsKey(next) -> { out.append(script.signs.getValue(next)); j++ }
                    // The inherent "a", except at the end of a word, where Hindi does not say it ("राम" = ram).
                    next == null || !isLetterOf(next, script) -> Unit
                    else -> out.append('a')
                }
                i = j
                continue
            }
            script.vowels[ch]?.let { out.append(it) }
                ?: script.signs[ch]?.let { out.append(it) }
                ?: when (ch) {
                    in script.nasal -> out.append('n')
                    script.visarga -> out.append('h')
                    in '०'..'९' -> out.append('0' + (ch - '०'))
                    in '০'..'৯' -> out.append('0' + (ch - '০'))
                    else -> Unit // halant, nukta, danda: nothing to say
                }
            i++
        }
        return out.toString()
    }

    private fun isLetterOf(c: Char, s: Script) =
        s.consonants.containsKey(c) || s.vowels.containsKey(c) || s.signs.containsKey(c) || c in s.nasal || c == s.nukta || c == s.halant

    private fun nuktaForm(c: Char): String? = when (c) {
        'क' -> "q"; 'ख' -> "kh"; 'ग' -> "g"; 'ज' -> "z"; 'ड' -> "r"; 'ढ' -> "rh"; 'फ' -> "f"; 'य' -> "y"
        'ড' -> "r"; 'ঢ' -> "rh"; 'য' -> "y"
        else -> null
    }

    private val FOLDS = listOf(
        "chh" to "c", "ch" to "c", "sh" to "s", "ph" to "f", "kh" to "k", "gh" to "g", "jh" to "j", "th" to "t",
        "dh" to "d", "bh" to "b", "ck" to "k", "z" to "j", "w" to "v", "q" to "k", "x" to "ks",
        "ee" to "i", "oo" to "u", "aa" to "a", "ai" to "e", "au" to "o", "ou" to "o", "oi" to "e", "iy" to "i",
    )

    /**
     * The sound of a name in plain letters, the same for its Hindi, Bengali and English spellings:
     * "रिया", "Riya", "Ria", "রিয়া" → "ria"; "झंडू बाम", "Zandu Balm" → "jandu bam" / "jandu balm".
     * Words are kept apart (space separated).
     */
    fun phonetic(text: String): String {
        var s = latin(text).lowercase()
            .replace(Regex("[^a-z0-9]+"), " ").trim()
        for ((from, to) in FOLDS) s = s.replace(from, to)
        // Doubled letters sound like one ("Rajjo" = "Rajo"), and a final "h" is silent ("Rekhah").
        s = s.replace(Regex("([a-z])\\1+"), "$1").replace(Regex("(?<=[a-z])h\\b"), "")
        return s.replace(Regex("\\s+"), " ").trim()
    }
}
