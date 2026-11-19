package com.raju.aiassistant.domain.voice

/**
 * How Cartesia is asked to sound soft, warm, unhurried and smiling — never fast or newsreader-flat.
 *
 * Sonic 3 and newer read `generation_config` (a speed number 0.6–1.5 and one emotion word such as
 * "affectionate"). Older Sonic models read `voice.__experimental_controls` (speed "slow" and emotion tags
 * such as "positivity:high"; Cartesia's tag set is anger / positivity / surprise / sadness / curiosity, so
 * there is no "affection" tag to send there). Each model gets only the controls it understands.
 */
sealed interface CartesiaControls {
    data class Generation(val speed: Double, val emotion: String?) : CartesiaControls
    data class Experimental(val speed: String, val emotions: List<String>) : CartesiaControls

    companion object {
        /** Her voice a little slower than the reading speed: unhurried, about -15 % with the warm mood. */
        const val SOFT_SPEED = 0.92f
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
        fun forModel(modelId: String, rate: Float, savedEmotion: String?, mood: VoiceMood?): CartesiaControls {
            val speed = (rate * SOFT_SPEED).coerceIn(MIN_SPEED, MAX_SPEED)
            val emotion = when {
                savedEmotion == null -> null
                mood == VoiceMood.WHISPER -> "calm" // late night: soft and quiet, not bubbly
                mood == VoiceMood.CONTROLLED_UPSET -> "sympathetic"
                else -> savedEmotion
            }
            if (isSonic3(modelId)) return Generation(Math.round(speed * 100) / 100.0, emotion)
            val tags = when {
                savedEmotion == null -> emptyList()
                mood == VoiceMood.WHISPER -> listOf("positivity:low")
                mood == VoiceMood.CONTROLLED_UPSET -> listOf("sadness:low")
                else -> listOf("positivity:high")
            }
            return Experimental(if (speed < 0.9f) "slow" else "normal", tags)
        }
    }
}
