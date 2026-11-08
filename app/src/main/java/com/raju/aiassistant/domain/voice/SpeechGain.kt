package com.raju.aiassistant.domain.voice

import kotlin.math.abs
import kotlin.math.roundToInt

/** Loudest sample a quiet recording is raised to before it is sent for recognition (~75 % of full scale). */
const val STT_TARGET_PEAK = 24_000
/** Never raised more than this (so a near-silent room does not become loud hiss). */
const val STT_MAX_GAIN = 8f

/**
 * A quiet 16-bit little-endian recording, made louder before speech-to-text. The phone's voice
 * recognition microphone often records speech at a small fraction of full scale, and Deepgram then
 * returns nothing ("Speech was heard but no words were recognised", "wake heard: " empty). The
 * loudest sample is raised to [STT_TARGET_PEAK], at most [STT_MAX_GAIN] times; recordings that are
 * already loud, or silent, are returned unchanged. Peaks are measured ignoring the top 0.1 % so a
 * single click does not decide the gain.
 */
fun louderForStt(pcm: ByteArray): ByteArray {
    val samples = pcm.size / 2
    if (samples == 0) return pcm
    val levels = IntArray(samples) { i -> abs(((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xFF)).toShort().toInt()) }
    val sorted = levels.sortedArray()
    val peak = sorted[((samples - 1) * 0.999).toInt()]
    if (peak <= 0 || peak >= STT_TARGET_PEAK) return pcm
    val gain = minOf(STT_MAX_GAIN, STT_TARGET_PEAK.toFloat() / peak)
    if (gain <= 1.05f) return pcm
    val out = ByteArray(samples * 2)
    for (i in 0 until samples) {
        val v = ((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xFF)).toShort().toInt()
        val scaled = (v * gain).roundToInt().coerceIn(-32_768, 32_767)
        out[2 * i] = (scaled and 0xFF).toByte()
        out[2 * i + 1] = (scaled shr 8).toByte()
    }
    return out
}
