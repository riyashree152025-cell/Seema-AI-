package com.raju.aiassistant.domain.voice

/**
 * How Cartesia is asked to sound soft, warm, unhurried and smiling — never fast or newsreader-flat.
 *
 * Sonic 3 and newer read `generation_config` (a speed number 0.6–1.5 and one emotion word such as
 * "affectionate"). Older Sonic models read `voice.__experimental_controls` (a speed from -1 to 1, -0.15 for her
 * warm pace, and emotion tags such as "positivity:high"; Cartesia's tag set is anger / positivity / surprise /
 * sadness / curiosity — an "affection" tag does not exist there and would make the request fail, so the
 * affection goes through Sonic 3's "affectionate"). Each model gets only the controls it understands.
 */
sealed interface CartesiaControls {
    data class Generation(val speed: Double, val emotion: String?) : CartesiaControls
    data class Experimental(val speed: Double, val emotions: List<String>) : CartesiaControls

    companion object {
        /** Her voice a little slower than the reading speed: unhurried, exactly -15 % with the warm mood. */
        const val SOFT_SPEED = 0.914f
        private const val MIN_SPEED = 0.6f
        private const val MAX_SPEED = 1.5f

        fun isSonic3(modelId: String): Boolean {
            val m = modelId.trim().lowercase()
            return m == "sonic-latest" || Regex("^sonic-(\\d+)").find(m)?.groupValues?.get(1)?.toIntOrNull()?.let { it >= 3 } == true
        }

        /**
         * [rate] is her speaking rate after the mood (warm ≈ 0.93); [savedEmotion] is the user's choice
         * (null = "Natural", none sent); [mood] the mood of this reply.
         */
        fun forModel(modelId: String, rate: Float, savedEmotion: String?, mood: VoiceMood?, feeling: SpeechEmotion? = null): CartesiaControls {
            // The sentence's feeling moves the pace a little: slower when pouting, livelier when teasing.
            val pace = when (feeling) {
                SpeechEmotion.POUTING -> 0.94f
                SpeechEmotion.AFFECTIONATE -> 0.97f
                SpeechEmotion.PLAYFUL -> 1.04f
                else -> 1f
            }
            val speed = (rate * SOFT_SPEED * pace).coerceIn(MIN_SPEED, MAX_SPEED)
            val emotion = when {
                savedEmotion == null -> null
                mood == VoiceMood.WHISPER -> "calm" // late night: soft and quiet, not bubbly
                mood == VoiceMood.CONTROLLED_UPSET || feeling == SpeechEmotion.POUTING -> "sympathetic"
                feeling == SpeechEmotion.AFFECTIONATE -> "affectionate"
                feeling == SpeechEmotion.PLAYFUL -> "happy"
                feeling == SpeechEmotion.CALM -> "calm"
                else -> savedEmotion
            }
            if (isSonic3(modelId)) return Generation(Math.round(speed * 100) / 100.0, emotion)
            val tags = when {
                savedEmotion == null -> emptyList()
                mood == VoiceMood.WHISPER -> listOf("positivity:low")
                mood == VoiceMood.CONTROLLED_UPSET || feeling == SpeechEmotion.POUTING -> listOf("sadness:low")
                else -> listOf("positivity:high")
            }
            // The same pace on the older scale (0 = normal): -0.15 for the warm mood.
            return Experimental(Math.round((speed - 1f) * 100) / 100.0, tags)
        }
    }
}
