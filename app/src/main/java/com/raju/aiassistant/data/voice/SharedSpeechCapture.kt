package com.raju.aiassistant.data.voice

import com.raju.aiassistant.domain.voice.EndpointDetector
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * One spoken turn, cut from the frames of the shared microphone with the same endpointing the
 * Deepgram recorder uses ([EndpointDetector]). Pure: frames in, outcome out.
 */
class SpeechCapture(private val clock: () -> Long = System::currentTimeMillis) {
    sealed interface Outcome {
        class Done(val pcm: ByteArray) : Outcome
        data object NoSpeech : Outcome
    }

    private var detector = EndpointDetector(frameMs = MIC_FRAME_MS)
    private val out = ByteArrayOutputStream()
    private var decided = false
    private val startedAt = clock()

    /** Frames received in this turn, and how often "no speech" came back before the real time had passed. */
    var frames = 0L
        private set
    var earlyNoSpeech = 0
        private set

    /**
     * Null while the turn is still going, and after the decision (the decision is returned once). The
     * frame is written before the decision, as the original recorder did.
     *
     * "No speech" is only believed after [MIN_NO_SPEECH_WALL_MS] of real time. The detector counts frames,
     * and on a phone frames once arrived about 17 times faster than real time, so 7 s of "audio" passed in
     * 0.4 s and the turn ended in a loop. Such an early "no speech" starts the detector again.
     */
    fun feed(frame: ByteArray): Outcome? {
        if (decided) return null
        frames++
        out.write(frame)
        val outcome = when (detector.onFrame(rms(frame))) {
            EndpointDetector.State.DONE -> Outcome.Done(out.toByteArray())
            EndpointDetector.State.NO_SPEECH ->
                if (clock() - startedAt >= MIN_NO_SPEECH_WALL_MS) {
                    Outcome.NoSpeech
                } else {
                    earlyNoSpeech++
                    detector = EndpointDetector(frameMs = MIC_FRAME_MS)
                    out.reset()
                    null
                }
            else -> null
        }
        if (outcome != null) decided = true
        return outcome
    }

    /** Real time since the turn started, for the log. */
    fun elapsedMs(): Long = clock() - startedAt

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

/** Real time the detector's "no speech" timeout stands for (the same 4.5 s as [EndpointDetector]). */
const val MIN_NO_SPEECH_WALL_MS = 4_500L

/** The shared microphone could not be used for this turn (the recorder failed). */
class SharedMicException(message: String) : Exception(message)

/**
 * Records one spoken turn on the STT purpose of this hub and returns it, or null when no speech was
 * heard. While it runs, a wake listener on the same hub gets no frames (STT outranks WAKE); when it
 * ends the lease is released and the recorder closes unless another lease still holds it. Cancelling
 * the caller releases the lease too.
 */
suspend fun MicHub.recordSpeech(
    clock: () -> Long = System::currentTimeMillis,
    log: (String) -> Unit = {},
): ByteArray? {
    val capture = SpeechCapture(clock)
    val result = CompletableDeferred<ByteArray?>()
    val lease = lease(
        MicPurpose.STT,
        onFrame = { frame ->
            if (!result.isCompleted) {
                when (val outcome = capture.feed(frame)) {
                    is SpeechCapture.Outcome.Done -> result.complete(outcome.pcm)
                    SpeechCapture.Outcome.NoSpeech -> result.complete(null)
                    null -> Unit
                }
            }
        },
        onError = { reason -> result.completeExceptionally(SharedMicException(reason)) },
    )
    try {
        return result.await()
    } finally {
        lease.release()
        closeIfIdle()
        val ms = capture.elapsedMs()
        val fps = if (ms > 0) capture.frames * 1000 / ms else 0
        log("stt turn: ${capture.frames} frames in $ms ms (~$fps/s, real time is 50/s), early no-speech ${capture.earlyNoSpeech}")
    }
}
