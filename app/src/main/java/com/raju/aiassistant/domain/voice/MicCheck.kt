package com.raju.aiassistant.domain.voice

/** What the 3-second microphone test found. */
enum class MicVerdict { NO_AUDIO, VERY_QUIET, QUIET, GOOD }

/**
 * The verdict for the loudest level (0..1, RMS / 5 000) heard while the user spoke during the test:
 * no frames at all → the microphone is blocked or switched off; very quiet → speak closer / check the
 * mic hole; quiet → works, Seema raises it before recognition; good otherwise.
 */
fun micVerdict(framesHeard: Int, loudest: Float): MicVerdict = when {
    framesHeard == 0 -> MicVerdict.NO_AUDIO
    loudest < 0.04f -> MicVerdict.VERY_QUIET
    loudest < 0.15f -> MicVerdict.QUIET
    else -> MicVerdict.GOOD
}

fun micVerdictText(v: MicVerdict, loudest: Float): String {
    val pct = (loudest * 100).toInt()
    return when (v) {
        MicVerdict.NO_AUDIO -> "✗ Mic se koi aawaz nahi aayi — Seema OFF hai, Mic permission band hai, ya koi aur app mic use kar raha hai."
        MicVerdict.VERY_QUIET -> "✗ Aawaz bahut dheemi ($pct%) — phone ke paas bolkar dobara test kijiye; mic ka chhed dhaka to nahi?"
        MicVerdict.QUIET -> "✓ Mic chal raha hai, aawaz thodi dheemi ($pct%) — Seema pehchaanne se pehle use badha deti hai."
        MicVerdict.GOOD -> "✓ Mic theek hai (aawaz $pct%)."
    }
}
