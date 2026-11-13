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

/**
 * The same raise for live recognition, frame by frame (the whole recording is not there yet). The level
 * of the loudest recent sound is followed: it jumps up at once (no clipping) and comes down slowly, and
 * each frame is multiplied so that this level lands near [STT_TARGET_PEAK], at most [STT_MAX_GAIN] times.
 * The gain rises gently, so a pause is not pumped up into noise, and the level learned in one turn starts
 * the next ([learnedPeak]) — the first word of a turn is already at the right loudness.
 */
class StreamGain(startPeak: Float = learnedPeak) {
    private val floor = STT_TARGET_PEAK / STT_MAX_GAIN
    private var env = startPeak.coerceIn(floor, STT_TARGET_PEAK.toFloat())
    private var gain = STT_TARGET_PEAK / env

    /** The current multiplier (1 … [STT_MAX_GAIN]). */
    val current: Float get() = gain

    /** A new array: 16-bit little-endian mono samples of [frame], made louder. */
    fun apply(frame: ByteArray): ByteArray {
        val samples = frame.size / 2
        var peak = 0
        for (i in 0 until samples) {
            val v = abs(((frame[2 * i + 1].toInt() shl 8) or (frame[2 * i].toInt() and 0xFF)).toShort().toInt())
            if (v > peak) peak = v
        }
        env = if (peak > env) peak.toFloat() else maxOf(floor, env * RELEASE)
        val wanted = (STT_TARGET_PEAK / env).coerceIn(1f, STT_MAX_GAIN)
        gain = if (wanted < gain) wanted else gain + (wanted - gain) * RISE
        learnedPeak = env
        val out = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val v = ((frame[2 * i + 1].toInt() shl 8) or (frame[2 * i].toInt() and 0xFF)).toShort().toInt()
            val scaled = (v * gain).roundToInt().coerceIn(-32_768, 32_767)
            out[2 * i] = (scaled and 0xFF).toByte()
            out[2 * i + 1] = (scaled shr 8).toByte()
        }
        return out
    }

    companion object {
        /** Per 20 ms frame: the followed level halves in about 1.4 s, so a quiet speaker is reached quickly. */
        private const val RELEASE = 0.99f
        private const val RISE = 0.05f
        /** The speech level of the last turn; a first turn starts at a 4 x raise. */
        @Volatile var learnedPeak: Float = STT_TARGET_PEAK / 4f
    }
}
