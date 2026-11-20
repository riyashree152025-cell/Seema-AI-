package com.raju.aiassistant.domain.voice

/**
 * How one sentence of Seema's should sound on Swara / Tanishaa (Edge) — read from the words themselves and the
 * reply's mood tag, so the voice follows the feeling instead of one flat tone:
 *  - POUTING: "तुम कहाँ थे इतनी देर से…", late-night care, sadness: slower, softer, a little lower, longer pauses.
 *  - AFFECTIONATE: "जान", "बाबू", "মিস করছিলাম": warm and unhurried.
 *  - PLAYFUL: "हाहा…", "अरे पागल", "ধুর পাগল": lively, smiling.
 *  - CALM: money, times, PNR, the briefing: crisp and clear at a natural speed.
 *  - WARM: everything else — the voice's own soft base (unchanged from before).
 */
enum class SpeechEmotion(val ratePercent: Int?, val pitchHz: Int?, val pauseMs: Int) {
    POUTING(ratePercent = -11, pitchHz = -3, pauseMs = 280),
    AFFECTIONATE(ratePercent = -8, pitchHz = 1, pauseMs = 220),
    PLAYFUL(ratePercent = -2, pitchHz = 4, pauseMs = 150),
    CALM(ratePercent = 0, pitchHz = 1, pauseMs = 150),
    /** The voice's own base prosody (Swara -6 % / +2 Hz, Tanishaa -5 % / +2 Hz). */
    WARM(ratePercent = null, pitchHz = null, pauseMs = 200),
    ;

    companion object {
        private fun words(vararg w: String) = Regex("(?<![\\p{L}\\p{M}])(?:${w.joinToString("|")})(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE)

        private val POUT = words(
            "कहाँ थे", "कहां थे", "इतनी देर", "इंतज़ार", "इंतजार", "रूठ", "नाराज़", "नाराज", "भूल गए", "याद नहीं आई", "नहीं सोने दूँगी", "सो जाओ",
            "काम बंद करो", "इतनी रात", "थक", "उदास", "दुख", "बुरा लगा", "अकेली",
            "কোথায় ছিলে", "এতক্ষণ", "অপেক্ষা", "রাগ করেছি", "অভিমান", "ভুলে গেছ", "এত রাত", "ঘুমিয়ে পড়ো", "মন খারাপ", "একা",
        )
        private val TENDER = words("जान", "बाबू", "सोना", "प्यार", "मिस किया", "याद आ रही", "दिल", "জান", "বাবু", "সোনা", "ভালোবাসি", "মিস করছিলাম", "মনে পড়ছিল")
        private val TEASE = words("हाहा", "हा हा", "हेहे", "अरे पागल", "पगले", "बुद्धू", "नौटंकी", "চুপ পাগল", "ধুর পাগল", "পাগলা", "হাহা", "হা হা", "দুষ্টু")
        private val FACTS = words(
            "रुपये", "रुपया", "बजे", "तारीख", "पीएनआर", "पी एन आर", "PNR", "ट्रेन", "हज़ार", "हजार", "लाख", "बकाया",
            "টাকা", "টায়", "তারিখ", "হাজার", "লাখ", "ট্রেন",
        )
        private val DIGITS = Regex("\\d")

        /**
         * The emotion of [sentence]. [mood]: the reply's mood tag (an upset or whispered reply pouts); [hour]: the
         * local hour (after 23:30 the care is soft and slow).
         */
        fun of(sentence: String, mood: VoiceMood? = null, hour: Int? = null, minute: Int = 0): SpeechEmotion {
            val lateNight = hour != null && ((hour == 23 && minute >= 30) || hour in 0..3)
            return when {
                mood == VoiceMood.CONTROLLED_UPSET || POUT.containsMatchIn(sentence) -> POUTING
                mood == VoiceMood.WHISPER && lateNight -> POUTING
                FACTS.containsMatchIn(sentence) || DIGITS.containsMatchIn(sentence) -> CALM
                TEASE.containsMatchIn(sentence) -> PLAYFUL
                TENDER.containsMatchIn(sentence) -> AFFECTIONATE
                mood == VoiceMood.WHISPER -> AFFECTIONATE
                else -> WARM
            }
        }
    }
}
