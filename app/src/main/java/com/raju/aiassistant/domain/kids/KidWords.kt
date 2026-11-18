package com.raju.aiassistant.domain.kids

import com.raju.aiassistant.domain.tools.Romanize

/**
 * Riya's own words: what a three-year-old says and what she means ("হুসু" → "পেশাব", "মালু" → "মালাই",
 * "গান্ধী বাবা মোরিয়া" → "গণপতি বাপ্পা মোরিয়া"). Learned from her own "হ্যাঁ" to Seema's question, from the
 * conversation, or taught by a parent ("মালু মানে মালাই"). Kept on the phone only; used only in Riya mode.
 */
data class KidWord(
    /** What she says, as the recogniser writes it. */
    val heard: String,
    /** What she means. */
    val meaning: String,
    val source: Source,
    val learnedAt: Long,
    val lastUsedAt: Long = learnedAt,
    val uses: Int = 0,
) {
    enum class Source { TAUGHT, CONFIRMED, GUESSED }
}

/** Where her words are kept (SharedPreferences on the phone; in memory in tests). */
interface KidWordStore {
    suspend fun all(): List<KidWord>
    suspend fun put(word: KidWord)
    suspend fun remove(heard: String)
}

class InMemoryKidWordStore : KidWordStore {
    private val words = linkedMapOf<String, KidWord>()
    override suspend fun all(): List<KidWord> = synchronized(words) { words.values.toList() }
    override suspend fun put(word: KidWord) { synchronized(words) { words[KidLexicon.key(word.heard)] = word } }
    override suspend fun remove(heard: String) { synchronized(words) { words.remove(KidLexicon.key(heard)) } }
}

object KidLexicon {
    /** Words she has not used for this long are no longer sent to the AI (she has grown out of them). */
    const val FADE_MS = 45L * 24 * 3_600_000L
    /** At most this many words go with one request: the ones in her sentence first, then the most used. */
    const val MAX_IN_PROMPT = 40

    /** The same word in any script or spelling ("মালু", "मालू", "Malu") is one entry. */
    fun key(heard: String): String = Romanize.phonetic(heard).ifBlank { heard.trim().lowercase() }

    /** Her words in [text] (by sound, so the recogniser's spelling does not matter). */
    fun inText(text: String, words: List<KidWord>): List<KidWord> {
        val said = " ${Romanize.phonetic(text)} "
        return words.filter { w -> key(w.heard).let { k -> k.isNotBlank() && said.contains(" $k ") } }
    }

    /** What goes with a request: words in this sentence, then the most used ones that have not faded. */
    fun forPrompt(text: String, words: List<KidWord>, now: Long): List<KidWord> {
        val here = inText(text, words)
        val rest = words.filter { it !in here && (it.source == KidWord.Source.TAUGHT || now - it.lastUsedAt < FADE_MS) }
            .sortedWith(compareByDescending<KidWord> { it.uses }.thenByDescending { it.lastUsedAt })
        return (here + rest).take(MAX_IN_PROMPT)
    }

    // ------------------------------------------------------------ a parent teaching a word

    /** A parent teaching: ("মালু", "মালাই") from "মালু মানে মালাই", "मालू मतलब मलाई", "रिया हुसू बोले तो मतलब पेशाब". */
    fun teachCommand(text: String, requireName: Boolean = false): Pair<String, String>? {
        val t = text.trim().trimEnd('.', '!', '।', '?').replace(Regex("\\s+"), " ")
        if (t.length > 90 || text.trim().endsWith("?")) return null
        // Outside Riya mode only a sentence about her teaches ("रिया हुसू बोले तो मतलब पेशाब"): "love ka matlab
        // kya hai" is a question, not a word for Riya.
        if (requireName && !NAMED.containsMatchIn(t)) return null
        val m = TEACH.matchEntire(t) ?: return null
        val heard = m.groupValues[1].trim().trim('"', '\'', '“', '”')
        val meaning = m.groupValues[2].trim().trim('"', '\'', '“', '”')
        if (heard.isEmpty() || meaning.isEmpty() || heard.split(' ').size > 4 || meaning.split(' ').size > 6) return null
        if (QUESTION.containsMatchIn(meaning)) return null
        return heard to meaning
    }

    private val NAMED = Regex("^(?:(?:hey |he |हे )?(?:seema|सीमा|সীমা)[, ]+)?(?:riya|रिया|রিয়া)", RegexOption.IGNORE_CASE)
    private val QUESTION = Regex("(?<![\\p{L}\\p{M}])(?:kya|क्या|कौन|kaun|কী|কি|what|kaise|कैसे|কেমন)(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE)

    private val TEACH = Regex(
        "^(?:(?:hey |he |हे )?(?:seema|सीमा|সীমা)[, ]+)?(?:(?:riya|रिया|রিয়া)(?: ?(?:shri|श्री|শ্রী))? )?(?:(?:jab|जब|যখন) )?" +
            "(.{1,30}?)(?: (?:bole|bolti hai|बोले|बोलती है|বলে|বললে))?(?: (?:to|तो|তো))? " +
            "(?:matlab|मतलब|mane|মানে|यानी|यानि|meaning|means|का मतलब है|মানে হলো) (.{1,40})$",
        RegexOption.IGNORE_CASE,
    )

    // ------------------------------------------------------------ her yes / no to Seema's question

    private val YES = Regex("^(?:হ্যাঁ|হ্যা|হাঁ|হুঁ|হুম|হুঁ|হা|হ|हाँ|हां|हा|हूँ|हम्म|haan|han|ha|hmm|hu|ji|জি|ঠিক|ok|okay)(?:[ ,।!]+.*)?$", RegexOption.IGNORE_CASE)
    private val NO = Regex("^(?:না|নাহ|নো|नहीं|नही|ना|nahi|nahin|na|no)(?:[ ,।!]+.*)?$", RegexOption.IGNORE_CASE)

    /** True = she said yes, false = no, null = something else. */
    fun answer(text: String): Boolean? {
        val t = text.trim()
        return when {
            NO.matches(t) -> false
            YES.matches(t) -> true
            else -> null
        }
    }

    // ------------------------------------------------------------ what the AI found out

    /** The AI's notes in its reply: [ASK:মালু=মালাই] (it asked her) and [LEARN:মালু=মালাই] (sure from the talk). */
    data class Notes(val text: String, val ask: Pair<String, String>?, val learned: List<Pair<String, String>>)

    private val TAG = Regex("\\[(ASK|LEARN)\\s*:\\s*([^=\\]]{1,30})=([^\\]]{1,40})]", RegexOption.IGNORE_CASE)

    fun notes(reply: String): Notes {
        var ask: Pair<String, String>? = null
        val learned = mutableListOf<Pair<String, String>>()
        TAG.findAll(reply).forEach { m ->
            val pair = m.groupValues[2].trim() to m.groupValues[3].trim()
            if (pair.first.isEmpty() || pair.second.isEmpty() || key(pair.first) == key(pair.second)) return@forEach
            if (m.groupValues[1].equals("ASK", true)) ask = pair else learned += pair
        }
        // Anything else in square brackets or angle tags is never read out to her.
        val clean = reply.replace(TAG, "")
            .replace(Regex("<([a-zA-Z_]{1,20})[^>]{0,100}>.*?</\\1>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]{0,200}>"), "").replace(Regex("\\[[A-Z_]{2,12}:[^\\]]*]"), "")
            .replace(Regex("[ \\t]+"), " ").trim()
        return Notes(clean, ask, learned)
    }
}
