package com.raju.aiassistant.data.voice

import com.raju.aiassistant.domain.voice.EndpointDetector
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * One spoken turn, cut from the frames of the shared microphone with the same endpointing the
 * Deepgram recorder uses ([EndpointDetector]). Pure: frames in, outcome out.
 */
class SpeechCapture {
    sealed interface Outcome {
        class Done(val pcm: ByteArray) : Outcome
        data object NoSpeech : Outcome
    }

    private val detector = EndpointDetector(frameMs = MIC_FRAME_MS)
    private val out = ByteArrayOutputStream()
    private var decided = false

    /**
     * Null while the turn is still going, and after the decision (the decision is returned once). The
     * frame is written before the decision, as the original recorder did.
     */
    fun feed(frame: ByteArray): Outcome? {
        if (decided) return null
        out.write(frame)
        val outcome = when (detector.onFrame(rms(frame))) {
            EndpointDetector.State.DONE -> Outcome.Done(out.toByteArray())
            EndpointDetector.State.NO_SPEECH -> Outcome.NoSpeech
            else -> null
        }
        if (outcome != null) decided = true
        return outcome
    }

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

/** The shared microphone could not be used for this turn (the recorder failed). */
class SharedMicException(message: String) : Exception(message)

/**
 * Records one spoken turn on the STT purpose of this hub and returns it, or null when no speech was
 * heard. While it runs, a wake listener on the same hub gets no frames (STT outranks WAKE); when it
 * ends the lease is released and the recorder closes unless another lease still holds it. Cancelling
 * the caller releases the lease too.
 */
suspend fun MicHub.recordSpeech(): ByteArray? {
    val capture = SpeechCapture()
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
    }
}
