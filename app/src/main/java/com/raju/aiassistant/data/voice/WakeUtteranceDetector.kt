package com.raju.aiassistant.data.voice

import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The sleep-mode voice detector of [MicWakeListener], as a pure step function: feed it 20 ms frames
 * and it returns one whole utterance (with half a second from before it) when someone has spoken and
 * then paused. The thresholds and timings are the same as the original detector; this copy exists
 * so the shared-microphone path can be tested on the JVM.
 */
class WakeUtteranceDetector : FrameDetector {
    private val preRoll = ArrayDeque<ByteArray>()
    private val spare = ArrayDeque<ByteArray>() // recycled frame copies, so listening does not allocate per frame
    private var noise = -1.0
    private var loudFrames = 0
    private var recording: Recording? = null

    private class Recording(val out: ByteArrayOutputStream, val pauseThreshold: Double) {
        var quietMs = 0
        var speechMs = START_FRAMES * FRAME_MS
        var totalMs = 0
    }

    /** Returns the utterance's PCM when it is complete, otherwise null. [frame] is not kept. */
    override fun feed(frame: ByteArray): ByteArray? {
        val level = rms(frame)
        val rec = recording
        if (rec == null) {
            listen(frame, level)
            return null
        }
        rec.out.write(frame)
        rec.totalMs += FRAME_MS
        if (level < rec.pauseThreshold) rec.quietMs += FRAME_MS else {
            rec.quietMs = 0
            rec.speechMs += FRAME_MS
        }
        if (rec.quietMs < END_SILENCE_MS && rec.totalMs < MAX_UTTERANCE_MS) return null
        recording = null
        if (rec.speechMs >= MIN_SPEECH_MS) return rec.out.toByteArray()
        // Too short (a knock, a cough): keep waiting.
        recycleAll()
        loudFrames = 0
        return null
    }

    private fun listen(frame: ByteArray, level: Double) {
        val copy = spare.pollLast() ?: ByteArray(MIC_FRAME_BYTES)
        frame.copyInto(copy)
        preRoll.addLast(copy)
        if (preRoll.size > PRE_ROLL_FRAMES) spare.addLast(preRoll.pollFirst())

        noise = when {
            noise < 0 -> level
            level < noise -> noise * 0.9 + level * 0.1
            else -> noise * 0.998 + level * 0.002
        }
        loudFrames = if (level > max(noise * 2.2, MIN_SPEECH)) loudFrames + 1 else 0
        if (loudFrames < START_FRAMES) return

        // Someone started speaking: the pre-roll goes first, then the rest until a pause.
        val out = ByteArrayOutputStream()
        preRoll.forEach { out.write(it) }
        recording = Recording(out, max(noise * 1.3, MIN_SPEECH * 0.8))
    }

    private fun recycleAll() {
        while (preRoll.isNotEmpty()) spare.addLast(preRoll.pollFirst())
    }

    companion object {
        const val FRAME_MS = 20
        private const val START_FRAMES = 3 // ~60 ms of voice
        private const val PRE_ROLL_FRAMES = 25 // 0.5 s
        private const val MIN_SPEECH = 110.0
        private const val MIN_SPEECH_MS = 160
        private const val END_SILENCE_MS = 650
        private const val MAX_UTTERANCE_MS = 7_500

        private fun rms(frame: ByteArray): Double {
            var sum = 0.0
            var i = 0
            while (i + 1 < frame.size) {
                val s = ((frame[i + 1].toInt() shl 8) or (frame[i].toInt() and 0xFF)).toShort().toDouble()
                sum += s * s
                i += 2
            }
            return sqrt(sum / (frame.size / 2))
        }
    }
}
