package com.raju.aiassistant.domain.voice

import com.raju.aiassistant.core.audio.PcmAudio

/**
 * Her voices (Sarvam, Cartesia, Edge Neural) take their pauses from punctuation. Before a sentence goes out:
 * markdown, bullets and emoji are dropped, help-desk lines ("मैं आपकी कैसे सहायता कर सकती हूँ", "As an AI…")
 * are removed, a comma or "…" becomes a real, gentle pause, a soft breath ("…") comes before a tender word
 * ("अच्छा… जान, …"), and every sentence ends on a full stop (a finished tone, not a cut-off). A short quiet tail
 * is added to the audio so the last syllable is never clipped by the speaker.
 */
object WarmProsody {
    /** Quiet added after each sentence, so the speaker never cuts the last syllable. */
    const val TAIL_MS = 140

    private val DOTS = Regex("\\.{2,}")
    private val NO_SPACE_AFTER = Regex("([,،।…])(?=[\\p{L}\\p{N}])")
    private val SPACES = Regex("[ \\t]{2,}")
    private val BENGALI = Regex("[\\u0980-\\u09FF]")
    private val DEVANAGARI = Regex("[\\u0900-\\u097F]")

    private val MARKDOWN = Regex("[*_`#>~|]+")
    private val BULLET = Regex("(?m)^\\s*(?:[-•●▪◦·]|\\d+[.)])\\s+")
    private val EMOJI = Regex("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}]")
    /** Help-desk lines no loving partner says. */
    private val ROBOTIC = listOf(
        Regex("(?i)\\bas an ai(?: language model| assistant)?\\s*,?\\s*"),
        Regex("(?i)\\b(?:how (?:can|may) i (?:help|assist) you(?: today)?)\\s*\\??"),
        Regex("(?:मैं )?आपकी (?:क्या|कैसे) (?:सहायता|मदद) कर सकती हूँ\\s*[?？।]?"),
        Regex("एक (?:AI|एआई) (?:होने के नाते|के रूप में)\\s*,?\\s*"),
        Regex("আমি (?:আপনাকে|তোমাকে) কীভাবে সাহায্য করতে পারি\\s*[?？।]?"),
    )
    /**
     * A pet name said to him (followed by a comma or the end) right after another word gets a breath before it:
     * "अच्छा जान, सुनो" → "अच्छा… जान, सुनो". "अब सोना है" (to sleep) is followed by a word, so it is left alone.
     */
    private val TENDER = Regex("(?<=[\\p{L}\\p{M}]) (जान|सोना|बाबू|पगले|সোনা|বাবু|জান|পাগল|পাগলা)(?=\\s*[,!?।？]|\\s*$)")

    fun text(raw: String): String {
        var t = raw
        t = BULLET.replace(t, "")
        t = MARKDOWN.replace(t, "")
        t = EMOJI.replace(t, "")
        ROBOTIC.forEach { t = it.replace(t, "") }
        t = t.replace('\n', ' ').trim()
        if (t.isEmpty()) return t
        t = DOTS.replace(t, "…")
        t = TENDER.replace(t, "… $1")
        t = NO_SPACE_AFTER.replace(t, "$1 ")
        t = SPACES.replace(t, " ")
        val last = t.last()
        if (last.isLetterOrDigit() || Character.getType(last) == Character.NON_SPACING_MARK.toInt() || Character.getType(last) == Character.COMBINING_SPACING_MARK.toInt()) {
            t += if (BENGALI.containsMatchIn(t) || DEVANAGARI.containsMatchIn(t)) "।" else "."
        }
        return t
    }

    /** [pcm] with [TAIL_MS] of silence after it. */
    fun withTail(pcm: PcmAudio): PcmAudio {
        val bytes = (pcm.sampleRate.toLong() * TAIL_MS / 1000).toInt() * 2 * pcm.channels
        if (bytes <= 0) return pcm
        return PcmAudio(pcm.sampleRate, pcm.channels, pcm.data + ByteArray(bytes))
    }
}
