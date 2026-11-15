package com.raju.aiassistant.domain.voice

import com.raju.aiassistant.core.audio.PcmAudio
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

/**
 * A soft limiter for her voice: below [THRESHOLD] of full scale the sound is untouched; above it the
 * peaks are bent smoothly (tanh knee) so they can never reach full scale. A clipped peak is what crackles
 * on a small phone speaker; this keeps loud, emotional words clean. [gain] (a whisper is quieter) is
 * applied first, then the limit.
 */
object PcmLimiter {
    const val THRESHOLD = 0.80f

    fun process(audio: PcmAudio, gain: Float = 1f): PcmAudio {
        val data = audio.data
        val n = data.size / 2
        if (n == 0) return audio
        if (gain == 1f && peak(data) <= THRESHOLD) return audio // already safe: no copy
        val out = ByteArray(n * 2)
        for (i in 0 until n) {
            val s = ((data[2 * i + 1].toInt() shl 8) or (data[2 * i].toInt() and 0xFF)).toShort().toInt()
            val y = limit(s / 32768f * gain)
            val v = (y * 32767f).toInt().coerceIn(-32768, 32767)
            out[2 * i] = (v and 0xFF).toByte()
            out[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return PcmAudio(audio.sampleRate, audio.channels, out)
    }

    /** One sample in -1..1 (or beyond, after gain) to the limited value, always inside -1..1. */
    fun limit(x: Float): Float {
        val a = abs(x)
        if (a <= THRESHOLD) return x
        val knee = 1f - THRESHOLD
        return sign(x) * (THRESHOLD + knee * tanh((a - THRESHOLD) / knee))
    }

    /** The loudest sample as a share of full scale. */
    fun peak(data: ByteArray): Float {
        var max = 0
        for (i in 0 until data.size / 2) {
            val s = abs(((data[2 * i + 1].toInt() shl 8) or (data[2 * i].toInt() and 0xFF)).toShort().toInt())
            if (s > max) max = s
        }
        return max / 32768f
    }
}
