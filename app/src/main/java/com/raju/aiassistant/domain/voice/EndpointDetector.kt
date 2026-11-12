package com.raju.aiassistant.domain.voice

/**
 * Decides when the user has finished speaking, from the loudness (RMS) of short audio frames.
 * The first frames calibrate the room's noise level; speech is louder than that, and a pause
 * of [silenceMs] after speech ends the turn. Pure logic, so it is unit-tested without a microphone.
 */
class EndpointDetector(
    private val frameMs: Int = 20,
    /** Pause after speech that ends the turn. */
    private val silenceMs: Int = 1_400,
    /** No voice at all for this long: the turn ends as "no speech". */
    private val noSpeechTimeoutMs: Int = 4_500,
    private val maxMs: Int = 15_000,
    private val calibrationMs: Int = 300,
    /**
     * Quietest level that counts as a voice. Was 400, which on a real Samsung phone missed normal
     * speech (its voice-recognition microphone gave 120-400): the wake listener, at 110, heard the
     * same voice. 150 with 2.5 x the room noise still ignores a quiet room and fans.
     */
    private val minThreshold: Double = 150.0,
    private val noiseFactor: Double = 2.5,
    /**
     * Voice shorter than this (a cough, a door, a click) followed by a pause is not a turn: keep
     * waiting. Such bits were sent to Deepgram and came back as "no words were recognised" again
     * and again on the phone.
     */
    private val minSpeechMs: Int = 200,
    /**
     * How fast the room level follows a sound that stays (per frame, 0..1). The room was measured
     * only in the first 300 ms: a fan, TV or traffic that got louder afterwards stayed "speech"
     * for ever, so the turn ran to the 15 s limit and came back as "no words were recognised"
     * (750 frames in the phone logs). Now a steady sound becomes the new room level within about
     * 2 s and the pause after it is heard. Speech is never steady: the gaps between words pull the
     * level straight back down ([floorFallRate]), so a talking user is not cut.
     */
    private val floorRiseRate: Double = 0.005,
    private val floorFallRate: Double = 0.3,
    /**
     * A short command ("light on karo", "haan") is answered sooner: when less than [shortSpeechMs] of
     * voice was heard, a pause of [shortSilenceMs] ends the turn. Longer speech keeps [silenceMs], so a
     * thinking pause in a long sentence is not cut. (Off by default: both equal [silenceMs].)
     */
    private val shortSilenceMs: Int = silenceMs,
    private val shortSpeechMs: Int = 0,
) {
    enum class State { LISTENING, SPEAKING, DONE, NO_SPEECH }

    var state = State.LISTENING
        private set
    private var elapsed = 0
    private var noise = 0.0
    private var noiseFrames = 0
    private var loudRun = 0
    private var quietMs = 0
    private var loudMs = 0

    val heardSpeech: Boolean get() = state == State.SPEAKING || state == State.DONE

    fun onFrame(rms: Double): State {
        if (state == State.DONE || state == State.NO_SPEECH) return state
        elapsed += frameMs
        if (elapsed <= calibrationMs) {
            // The quietest frame, not the average: someone who starts talking at once must not
            // be measured as "room noise" (that raised the bar above their own voice).
            noise = if (noiseFrames == 0) rms else minOf(noise, rms)
            noiseFrames++
            return state
        }
        val threshold = maxOf(minThreshold, noise * noiseFactor)
        val loud = rms > threshold
        noise += (rms - noise) * (if (rms > noise) floorRiseRate else floorFallRate)
        when (state) {
            State.LISTENING -> {
                loudRun = if (loud) loudRun + 1 else 0
                if (loudRun >= 3) { // ~60 ms of voice, not a click
                    state = State.SPEAKING
                    loudMs = loudRun * frameMs
                    quietMs = 0
                } else if (elapsed >= noSpeechTimeoutMs) state = State.NO_SPEECH
            }
            State.SPEAKING -> {
                if (loud) loudMs += frameMs
                quietMs = if (loud) 0 else quietMs + frameMs
                val pauseNeeded = if (loudMs < shortSpeechMs) shortSilenceMs else silenceMs
                if (quietMs >= pauseNeeded) {
                    if (loudMs >= minSpeechMs) state = State.DONE
                    else { // only a short noise: wait for real speech
                        state = State.LISTENING
                        loudRun = 0
                        loudMs = 0
                        if (elapsed >= noSpeechTimeoutMs) state = State.NO_SPEECH
                    }
                }
            }
            else -> Unit
        }
        if (elapsed >= maxMs && state != State.DONE) state = if (heardSpeech) State.DONE else State.NO_SPEECH
        return state
    }
}

/**
 * How the next spoken turn is cut. A ringing call lasts only seconds and its answer is one or two words
 * ("uthao", "kaat do"), so while [quick] is on a turn ends after 0.6 s of quiet instead of 1.4 s and is
 * never longer than 6 s. Set by the engine just before it listens.
 */
object TurnPacing {
    @Volatile var quick: Boolean = false

    fun detector(frameMs: Int): EndpointDetector =
        if (quick) EndpointDetector(frameMs = frameMs, silenceMs = QUICK_SILENCE_MS, maxMs = QUICK_MAX_MS, minSpeechMs = 120)
        else EndpointDetector(
            frameMs = frameMs, silenceMs = NORMAL_SILENCE_MS,
            shortSilenceMs = SHORT_SILENCE_MS, shortSpeechMs = SHORT_SPEECH_MS,
        )

    const val QUICK_SILENCE_MS = 600
    /** A normal turn ends after 1.1 s of quiet (was 1.4 s); a short command after 0.8 s. */
    const val NORMAL_SILENCE_MS = 1_100
    const val SHORT_SILENCE_MS = 800
    const val SHORT_SPEECH_MS = 1_500
    const val QUICK_MAX_MS = 6_000
}
