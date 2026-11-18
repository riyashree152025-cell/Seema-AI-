package com.raju.aiassistant.domain.tools

/**
 * Letters spoken one by one in a name, as the recogniser writes them in Hindi: "साजिद शेख एन जेड एम" is the contact
 * saved as "Sajid Shaikh NZM". A run of two or more spoken letters becomes those Latin letters; a lone "जी" or
 * "के" stays (they are usually "ji" and "ke", not G and K).
 */
object SpokenLetters {
    private val LETTERS = mapOf(
        "ए" to "A", "बी" to "B", "सी" to "C", "डी" to "D", "ई" to "E", "एफ" to "F", "जी" to "G", "एच" to "H", "आई" to "I",
        "जे" to "J", "के" to "K", "एल" to "L", "एम" to "M", "एन" to "N", "ओ" to "O", "पी" to "P", "क्यू" to "Q", "आर" to "R",
        "एस" to "S", "टी" to "T", "यू" to "U", "वी" to "V", "डब्ल्यू" to "W", "डबल्यू" to "W", "एक्स" to "X", "वाई" to "Y",
        "जेड" to "Z", "ज़ेड" to "Z", "जैड" to "Z", "ज़ैड" to "Z",
    )
    /** Letters that are never ordinary Hindi words: a run must contain one of these. */
    private val CLEAR = setOf("एफ", "एच", "एल", "एम", "एन", "क्यू", "आर", "एस", "टी", "डब्ल्यू", "डबल्यू", "एक्स", "वाई", "जेड", "ज़ेड", "जैड", "ज़ैड", "बी", "डी", "पी", "वी")

    /** "साजिद शेख एन जेड एम" → "साजिद शेख NZM"; text without spelled letters is returned unchanged. */
    fun collapse(text: String): String {
        val words = text.trim().split(Regex("\\s+"))
        val out = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            var j = i
            while (j < words.size && LETTERS.containsKey(words[j])) j++
            val run = words.subList(i, j)
            if (run.size >= 2 && run.any { it in CLEAR }) {
                out += run.joinToString("") { LETTERS.getValue(it) }
                i = j
            } else {
                out += words[i]
                i++
            }
        }
        return out.joinToString(" ")
    }

    /**
     * Shorter forms of a name to try when the whole name is not found: "साजिद शेख एन जेडम" → "साजिद शेख एन",
     * "साजिद शेख", "साजिद". Each result is still confirmed by the user before anything is done.
     */
    fun shorterForms(name: String): List<String> {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return (words.size - 1 downTo 1).map { words.take(it).joinToString(" ") }.filter { it.length >= 3 }
    }
}
