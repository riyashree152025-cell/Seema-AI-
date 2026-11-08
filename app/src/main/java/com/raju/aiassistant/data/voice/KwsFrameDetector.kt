package com.raju.aiassistant.data.voice

import kotlin.math.sqrt

/** One keyword-spotting stream (sherpa-onnx on the phone, a fake in tests). */
interface KeywordStream {
    /** Adds 16 kHz mono samples in [-1, 1]; true when the wake word was spotted. */
    fun accept(samples: FloatArray): Boolean

    fun release()
}

/** 100 ms per batch: the spotter decodes once per batch, not on every 20 ms frame (CPU and battery). */
const val KWS_BATCH_SAMPLES = 1_600

/**
 * The offline wake word on the shared microphone: gathers 20 ms frames into [batchSamples] batches
 * (the original KWS listener read 100 ms at a time too) and hands each batch to the spotter. The
 * stream is opened on the first full batch, on the frame thread, so the model never loads on the
 * caller's (main) thread. Returns [HEARD] once the phrase is spotted.
 */
class KwsFrameDetector(
    private val openStream: () -> KeywordStream,
    private val batchSamples: Int = KWS_BATCH_SAMPLES,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) : FrameDetector {
    private var stream: KeywordStream? = null
    private val batch = FloatArray(batchSamples)
    private var filled = 0
    private var sumSquares = 0.0
    private var lastLevelLog = 0L

    /** Batches handed to the spotter so far. */
    var batches = 0
        private set

    override fun feed(frame: ByteArray): ByteArray? {
        var i = 0
        while (i + 1 < frame.size) {
            val sample = ((frame[i + 1].toInt() shl 8) or (frame[i].toInt() and 0xFF)).toShort()
            batch[filled++] = sample / 32768f
            sumSquares += sample.toDouble() * sample
            i += 2
            if (filled == batchSamples && flush()) return HEARD
        }
        return null
    }

    private fun flush(): Boolean {
        // For the phone log: whether a voice reaches the spotter at all (at most every 3 s).
        val level = sqrt(sumSquares / filled)
        val now = clock()
        if (level > VOICE_LEVEL && now - lastLevelLog > 3_000) {
            lastLevelLog = now
            log("kws hears sound, level ${level.toInt()}")
        }
        filled = 0
        sumSquares = 0.0
        batches++
        val s = stream ?: openStream().also { stream = it }
        val heard = s.accept(batch)
        if (heard) log("kws: wake word")
        return heard
    }

    override fun close() {
        stream?.release()
        stream = null
    }

    companion object {
        /** Result for "her name was spotted": no audio is passed on. */
        val HEARD = ByteArray(0)

        /** Microphone level (RMS of 16-bit samples) above a quiet room: someone may be talking. */
        const val VOICE_LEVEL = 150.0
    }
}
