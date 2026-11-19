package com.raju.aiassistant.domain.voice

/**
 * Seema's three speaking moods. Every feeling is mapped onto one of these clean profiles so her voice
 * never cracks on a phone speaker: no high-pitched shouting for anger, a lower and calmer voice instead.
 * [pitch] is used by the phone's own voice (Android TTS); [rate] multiplies the user's speech-rate
 * setting for every voice; [gain] scales the loudness before the limiter.
 */
enum class VoiceMood(val pitch: Float, val rate: Float, val gain: Float, val eyeArgb: Long) {
    /** Love, happy, playful, normal: smooth, clear, sweet. */
    WARM(pitch = 1.03f, rate = 0.93f, gain = 1.0f, eyeArgb = 0xFF18FFFF),
    /** Anger, scolding, sadness, complaint: calm, deep and grounded instead of loud. */
    CONTROLLED_UPSET(pitch = 0.90f, rate = 0.88f, gain = 1.0f, eyeArgb = 0xFFE53950),
    /** Late night or a soft secret: slower and quieter. */
    WHISPER(pitch = 0.96f, rate = 0.84f, gain = 0.65f, eyeArgb = 0xFF5C7CFA);

    companion object {
        /** The mood when the reply names none: a soft whisper late at night, warm otherwise. */
        fun defaultFor(hour: Int): VoiceMood = if (hour >= 22 || hour < 6) WHISPER else WARM
    }
}

/**
 * Reads the optional mood tag the AI puts in front of a reply ("[MOOD:WARM] Jaan, …") and removes every
 * mood tag from the text, so a tag is never shown or read aloud. Unknown moods are removed too.
 */
object VoiceMoodParser {
    private val TAG = Regex("\\[\\s*MOOD\\s*:\\s*([A-Za-z_ ]+?)\\s*]", RegexOption.IGNORE_CASE)

    data class Parsed(val mood: VoiceMood?, val text: String)

    fun parse(text: String): Parsed {
        val first = TAG.find(text)
        // Only a tag at the very start chooses the mood; any tag anywhere is still removed.
        val mood = first?.takeIf { text.substring(0, it.range.first).isBlank() }?.groupValues?.get(1)?.let(::moodOf)
        val clean = TAG.replace(text, "").replace(Regex("[ \\t]{2,}"), " ").trim()
        return Parsed(mood, clean)
    }

    /** A mood name as the AI may write it ("warm", "controlled upset", "WHISPER"). */
    fun moodOf(name: String): VoiceMood? = when (name.trim().uppercase().replace(' ', '_')) {
        "WARM", "LOVE", "HAPPY", "PLAYFUL", "NORMAL" -> VoiceMood.WARM
        "CONTROLLED_UPSET", "UPSET", "ANGRY", "ANGER", "SAD", "TAPORI", "COMPLAINT" -> VoiceMood.CONTROLLED_UPSET
        "WHISPER", "SOFT", "SECRET" -> VoiceMood.WHISPER
        else -> null
    }
}
