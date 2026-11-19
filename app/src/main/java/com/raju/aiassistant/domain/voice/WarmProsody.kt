package com.raju.aiassistant.domain.voice

import com.raju.aiassistant.core.audio.PcmAudio

/**
 * Her cloud voices (Sarvam, Cartesia, ElevenLabs) take their pauses from punctuation. The text is tidied so a
 * comma or "…" becomes a real, gentle pause, and every sentence ends on a full stop (a falling, finished tone,
 * not a cut-off). A short quiet tail is added to the audio so the last syllable is never clipped by the speaker.
 */
object WarmProsody {
    /** Quiet added after each sentence, so the speaker never cuts the last syllable. */
    const val TAIL_MS = 140

    private val DOTS = Regex("\\.{2,}")
    private val NO_SPACE_AFTER = Regex("([,،।…])(?=[\\p{L}\\p{N}])")
    private val SPACES = Regex("[ \\t]{2,}")
    private val BENGALI = Regex("[\\u0980-\\u09FF]")
    private val DEVANAGARI = Regex("[\\u0900-\\u097F]")

    fun text(raw: String): String {
        var t = raw.trim()
        if (t.isEmpty()) return t
        t = DOTS.replace(t, "…")
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
