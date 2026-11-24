package com.raju.aiassistant.domain.voice

/**
 * The strict gate in front of everything Seema hears on her own (hands-free, standby, behind another app): a
 * sentence goes on only when it calls her by name ("Hey Seema …", "हे सीमा …", "অ্যাই সীমা …") or the short
 * follow-up window after her own reply is open ([ConversationSessionTimer]). Raju talking to someone else, to
 * another assistant, or the TV is dropped silently: never sent to an AI, never answered.
 */
object WakeWordGate {
    sealed interface Verdict {
        /** Called by name: [request] is what came after it ("" = only her name). */
        data class Called(val request: String) : Verdict
        /** No name, but her follow-up window is open: the whole sentence is for her. */
        data class FollowUp(val request: String) : Verdict
        /** Spoken to Google / Gemini / Alexa / Siri …: not for her; the follow-up window closes. */
        data object OtherAssistant : Verdict
        /** Not for her: dropped without a word. */
        data object Ignore : Verdict
    }

    private fun words(vararg w: String) = Regex("(?<![\\p{L}\\p{M}])(?:${w.joinToString("|")})(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE)

    /** Her name as the recognisers write it — the real name only (not "seem", "Simran" …). */
    private val NAME = words(
        "seema", "seemaa", "sima", "seemaji", "seema ji", "heyseema", "hiseema", "mesima", "meseema", "hesima", "heseema",
        "areseema", "areyseema", "sunoseema",
        "सीमा", "सिमा", "सीमाजी", "हेसीमा", "अरेसीमा", "सुनोसीमा",
        "সীমা", "সিমা", "সীমাদি", "হেইসীমা", "এইসীমা", "শোনোসীমা",
    )

    /** Someone else's assistant, or the words that wake it. */
    private val OTHER = words(
        "hey google", "ok google", "okay google", "gemini", "alexa", "siri", "chatgpt", "chat gpt", "hey meta", "meta ai", "bixby", "copilot", "cortana",
        "हे गूगल", "ओके गूगल", "जेमिनी", "जैमिनी", "एलेक्सा", "अलेक्सा", "सिरी", "चैटजीपीटी",
        "হেই গুগল", "ওকে গুগল", "জেমিনি", "অ্যালেক্সা", "সিরি",
    )
    /** "Google pe search karo" is a request to her that names Google; only an address at the start is for the other one. */
    private val OTHER_AT_START = Regex("^(?:(?:hey|ok|okay|hi|हे|ओके|হেই|ওকে)\\s+)?" + OTHER.pattern.removePrefix("(?<![\\p{L}\\p{M}])"), RegexOption.IGNORE_CASE)

    /** "Hey Seema aaj kitna paisa kata" → "aaj kitna paisa kata"; "" for her name alone; null without her name. */
    fun request(heard: String): String? {
        val t = heard.trim()
        if (!NAME.containsMatchIn(t)) return null
        return afterWakeWord(t)
    }

    /** Talking to another assistant ("Hey Google …", "Gemini, …", "Alexa"): not for her. */
    fun addressesOther(heard: String): Boolean = OTHER_AT_START.containsMatchIn(heard.trim())

    /**
     * What to do with [heard]. [followUpOpen]: her short window after a reply (or a tap on the mic) is open.
     */
    fun check(heard: String, followUpOpen: Boolean): Verdict {
        val t = heard.trim()
        if (t.isEmpty()) return Verdict.Ignore
        request(t)?.let { return Verdict.Called(it) }
        if (!followUpOpen) return Verdict.Ignore
        if (addressesOther(t)) return Verdict.OtherAssistant
        return Verdict.FollowUp(t)
    }

    /** "Seema chup raho", "so jao", "abhi mat suno", "সীমা চুপ থাকো": wake-word-only mode now. */
    private val QUIET = Regex(
        "^(?:(?:ab|abhi|achha|acha|thik hai|theek hai|ok|chalo|अब|अभी|अच्छा|ठीक है|चलो|এবার|এখন|আচ্ছা|ঠিক আছে)[,\\s]+)?" +
            "(?:chup raho|chup ho jao|chup rahiye|chup|mat suno|abhi mat suno|mat bolo|sunna band karo|so jao|so ja|sojao|so jaiye|" +
            "चुप रहो|चुप हो जाओ|चुप रहिए|चुप|मत सुनो|अभी मत सुनो|मत बोलो|सुनना बंद करो|सो जाओ|सो जा|सो जाइए|" +
            "চুপ থাকো|চুপ করো|চুপ কর|চুপ|শুনো না|এখন শুনো না|ঘুমিয়ে পড়ো|ঘুমাও|go to sleep|sleep mode)" +
            "(?:\\s+(?:ab|abhi|अब|अभी|এবার|এখন|please|प्लीज़))?$",
        RegexOption.IGNORE_CASE,
    )

    fun isQuietCommand(text: String): Boolean {
        val t = text.trim().trimEnd('.', '!', '।', ' ').replace(Regex("\\s+"), " ")
        val rest = request(t) ?: t
        return QUIET.matches(rest.trim().trimEnd('.', '!', '।', ' ').trimStart(',', ' '))
    }
}
