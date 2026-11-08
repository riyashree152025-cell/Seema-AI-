package com.raju.aiassistant.domain.voice

/**
 * Loudness below which a sound heard while she sleeps is not sent online to check for her name:
 * RMS ≈ 110 of 32 768, the same floor the wake detector uses for speech. (It was 300: on the
 * phone, real speech through the voice-recognition mic often stays between 110 and 300.)
 */
const val WAKE_ENERGY_THRESHOLD = 0.022f

/** Misses (sounds without her name) in a row before checks pause; then 5 s, at most 10 s. */
const val WAKE_MISSES_BEFORE_BACKOFF = 6
const val WAKE_BACKOFF_MAX_MS = 10_000L
/** A quiet room for this long forgets earlier misses. */
const val WAKE_MISS_MEMORY_MS = 120_000L

/**
 * Loudest 100 ms of a 16 kHz 16-bit little-endian clip, 0..1 (RMS / 5 000, like the microphone level).
 * A clip shorter than 100 ms is measured whole.
 */
fun loudestLevel(pcm: ByteArray, windowSamples: Int = 1_600): Float {
    val samples = pcm.size / 2
    if (samples == 0) return 0f
    val win = minOf(windowSamples, samples)
    var best = 0.0
    var start = 0
    while (start < samples) {
        val end = minOf(start + win, samples)
        var sum = 0.0
        for (i in start until end) {
            val v = ((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xFF)).toShort().toInt()
            sum += v.toDouble() * v
        }
        best = maxOf(best, sum / (end - start))
        start += win
    }
    return (kotlin.math.sqrt(best) / 5_000.0).toFloat().coerceIn(0f, 1f)
}

/**
 * Decides whether a sound heard during "Hey Seema" standby goes online (Deepgram) to check for her
 * name. Faint sounds never do. After several sounds in a row without her name (a TV, people talking
 * nearby) checks pause for a short, growing time, so background noise cannot keep the internet and
 * battery busy. Her name resets everything.
 */
class WakeCheckGate(private val clock: () -> Long, private val minLevel: Float = WAKE_ENERGY_THRESHOLD) {
    enum class Decision { CHECK, TOO_QUIET, BACKING_OFF }

    private var misses = 0
    private var lastMissAt = 0L
    private var pauseUntil = 0L

    fun decide(pcm: ByteArray): Decision {
        val now = clock()
        if (now < pauseUntil) return Decision.BACKING_OFF
        if (loudestLevel(pcm) < minLevel) return Decision.TOO_QUIET
        return Decision.CHECK
    }

    /** After a check: [nameHeard] = her name was in it. */
    fun onChecked(nameHeard: Boolean) {
        val now = clock()
        if (nameHeard) {
            misses = 0
            pauseUntil = 0
            return
        }
        if (now - lastMissAt > WAKE_MISS_MEMORY_MS) misses = 0
        misses++
        lastMissAt = now
        if (misses >= WAKE_MISSES_BEFORE_BACKOFF) pauseUntil = now + backoffMs(misses)
    }

    companion object {
        fun backoffMs(misses: Int): Long {
            val steps = (misses - WAKE_MISSES_BEFORE_BACKOFF).coerceIn(0, 8)
            return minOf(WAKE_BACKOFF_MAX_MS, 5_000L shl steps)
        }
    }
}
