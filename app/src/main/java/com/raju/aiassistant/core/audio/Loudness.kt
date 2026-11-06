package com.raju.aiassistant.core.audio

import kotlin.math.abs

/** Loudest sample after [louder]: just below full scale, so the voice is loud but never clips. */
private const val TARGET_PEAK = 31_000

/** Never amplify more than this (quiet noise or near-silence must not become a roar). */
private const val MAX_GAIN = 6.0

/**
 * Raises quiet speech so its loudest sample is near full scale. Cloud voices (e.g. Sarvam) often
 * arrive at a third of full scale and sounded too soft on the phone's speaker. Audio that is
 * already loud is returned unchanged.
 */
fun louder(audio: PcmAudio): PcmAudio {
    val d = audio.data
    var peak = 0
    var i = 0
    while (i + 1 < d.size) {
        val s = ((d[i + 1].toInt() shl 8) or (d[i].toInt() and 0xFF)).toShort().toInt()
        val a = abs(s)
        if (a > peak) peak = a
        i += 2
    }
    if (peak == 0) return audio
    val gain = minOf(MAX_GAIN, TARGET_PEAK.toDouble() / peak)
    if (gain <= 1.05) return audio
    val out = ByteArray(d.size)
    i = 0
    while (i + 1 < d.size) {
        val s = ((d[i + 1].toInt() shl 8) or (d[i].toInt() and 0xFF)).toShort().toInt()
        val v = (s * gain).toInt().coerceIn(-32_768, 32_767)
        out[i] = (v and 0xFF).toByte()
        out[i + 1] = ((v shr 8) and 0xFF).toByte()
        i += 2
    }
    if (d.size % 2 == 1) out[d.size - 1] = d[d.size - 1]
    return PcmAudio(audio.sampleRate, audio.channels, out)
}
