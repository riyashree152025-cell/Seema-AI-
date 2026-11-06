package com.raju.aiassistant.domain.language

/**
 * Hindi (Devanagari) and Bengali letters written in Latin letters the way people type Hinglish:
 * "रिजवान ट्रैवल्स" -> "rijavan traivals", "दादा पेमेंट नहीं आया" -> "dada pement nahin aaya".
 * Latin text, digits and punctuation pass through unchanged. Used to compare contact names said in
 * one script with names saved in another, and to write a message "in Hinglish".
 */
object Transliterate {
    private val CONSONANTS = mapOf<Char, String>(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'ळ' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        '\u0958' to "q", '\u0959' to "kh", '\u095A' to "g", '\u095B' to "z", '\u095C' to "r", '\u095D' to "rh", '\u095E' to "f", '\u095F' to "y",
        // Bengali
        'ক' to "k", 'খ' to "kh", 'গ' to "g", 'ঘ' to "gh", 'ঙ' to "n",
        'চ' to "ch", 'ছ' to "chh", 'জ' to "j", 'ঝ' to "jh", 'ঞ' to "n",
        'ট' to "t", 'ঠ' to "th", 'ড' to "d", 'ঢ' to "dh", 'ণ' to "n",
        'ত' to "t", 'থ' to "th", 'দ' to "d", 'ধ' to "dh", 'ন' to "n",
        'প' to "p", 'ফ' to "ph", 'ব' to "b", 'ভ' to "bh", 'ম' to "m",
        'য' to "j", 'র' to "r", 'ল' to "l", 'শ' to "sh", 'ষ' to "sh", 'স' to "s", 'হ' to "h",
        '\u09DC' to "r", '\u09DD' to "rh", '\u09DF' to "y",
    )

    /** Nukta forms written as letter + ़ (U+093C) / ় (U+09BC). */
    private val NUKTA = mapOf('क' to "q", 'ख' to "kh", 'ग' to "g", 'ज' to "z", 'ड' to "r", 'ढ' to "rh", 'फ' to "f", 'য' to "y", 'ড' to "r", 'ঢ' to "rh")

    private val VOWELS = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o", 'ऍ' to "e",
        'অ' to "o", 'আ' to "a", 'ই' to "i", 'ঈ' to "i", 'উ' to "u", 'ঊ' to "u", 'ঋ' to "ri",
        'এ' to "e", 'ঐ' to "oi", 'ও' to "o", 'ঔ' to "ou",
    )

    private val SIGNS = mapOf(
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ॅ' to "e",
        'া' to "a", 'ি' to "i", 'ী' to "i", 'ু' to "u", 'ূ' to "u", 'ৃ' to "ri",
        'ে' to "e", 'ৈ' to "oi", 'ো' to "o", 'ৌ' to "ou",
    )

    private const val VIRAMA_HI = '्'
    private const val VIRAMA_BN = '্'

    private fun isIndic(ch: Char) = ch in 'ऀ'..'৿'

    /** True when [text] has Hindi or Bengali letters. */
    fun hasIndic(text: String): Boolean = text.any(::isIndic)

    /**
     * Latin spelling of [text]. Long vowels inside a word are written "aa"/"ee" only where Hinglish
     * usually does ("aaya", "nahin"); the inherent "a" at the end of a word is dropped ("ram", not "rama").
     */
    fun toLatin(text: String): String {
        if (!hasIndic(text)) return text
        val out = StringBuilder()
        var i = 0
        // Index in [out] of an inherent "a" just written, so a following sign can replace it.
        var pendingA = -1
        while (i < text.length) {
            val ch = text[i]
            val next = text.getOrNull(i + 1)
            when {
                CONSONANTS.containsKey(ch) -> {
                    var latin = CONSONANTS.getValue(ch)
                    if ((next == '़' || next == '়') && NUKTA.containsKey(ch)) {
                        latin = NUKTA.getValue(ch)
                        i++
                    }
                    out.append(latin)
                    out.append('a')
                    pendingA = out.length - 1
                }
                SIGNS.containsKey(ch) -> {
                    val sign = SIGNS.getValue(ch)
                    if (pendingA >= 0 && pendingA == out.length - 1) out.setLength(pendingA)
                    out.append(sign)
                    pendingA = -1
                }
                ch == VIRAMA_HI || ch == VIRAMA_BN -> {
                    if (pendingA >= 0 && pendingA == out.length - 1) out.setLength(pendingA)
                    pendingA = -1
                }
                ch == 'ं' || ch == 'ँ' || ch == 'ং' || ch == 'ঁ' -> { out.append('n'); pendingA = -1 }
                ch == 'ः' || ch == 'ঃ' -> { out.append('h'); pendingA = -1 }
                ch == '़' || ch == '়' -> Unit
                VOWELS.containsKey(ch) -> { out.append(VOWELS.getValue(ch)); pendingA = -1 }
                ch in '०'..'९' -> { out.append('0' + (ch - '०')); pendingA = -1 }
                ch in '০'..'৯' -> { out.append('0' + (ch - '০')); pendingA = -1 }
                ch == '।' || ch == '॥' -> { out.append('.'); pendingA = -1 }
                isIndic(ch) -> Unit // other marks
                else -> {
                    dropFinalA(out, pendingA)
                    out.append(ch)
                    pendingA = -1
                }
            }
            i++
        }
        dropFinalA(out, pendingA)
        return out.toString()
    }

    /** "ram" not "rama", "aaya" stays (that "a" is a written vowel sign). */
    private fun dropFinalA(out: StringBuilder, pendingA: Int) {
        if (pendingA >= 0 && pendingA == out.length - 1 && pendingA >= 2) out.setLength(pendingA)
    }
}
