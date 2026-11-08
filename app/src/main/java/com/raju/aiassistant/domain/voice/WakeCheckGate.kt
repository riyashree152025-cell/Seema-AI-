package com.raju.aiassistant.domain.voice

/**
 * Loudness below which a sound heard while she sleeps is not sent online to check for her name:
 * RMS ≈ 110 of 32 768, the same floor the wake detector uses for speech. (It was 300: on the
 * phone, real speech through the voice-recognition mic often stays between 110 and 300.)
 */
const val WAKE_ENERGY_THRESHOLD = 0.022f

/**
 * How far above the room's own sound a clip must reach to be checked: RMS 35 of 32 768 (level 0.007).
 * The bar is max(110 RMS, room + 35 RMS).
 */
const val WAKE_ABOVE_ROOM = 0.007f
/** How fast the room level follows each new clip (0..1). */
const val WAKE_ROOM_FOLLOW = 0.3f

/**
 * Loudest 100 ms of a 16 kHz 16-bit little-endian clip, 0..1 (RMS / 5 000, like the microphone level).
 * A clip shorter than 100 ms is measured whole.
 */
fun loudestLevel(pcm: ByteArray, windowSamples: Int = 1_600): Float = windowLevel(pcm, windowSamples, loudest = true)

/** Quietest 100 ms of the clip (the room between words), on the same 0..1 scale. */
fun quietestLevel(pcm: ByteArray, windowSamples: Int = 1_600): Float = windowLevel(pcm, windowSamples, loudest = false)

private fun windowLevel(pcm: ByteArray, windowSamples: Int, loudest: Boolean): Float {
    val samples = pcm.size / 2
    if (samples == 0) return 0f
    val win = minOf(windowSamples, samples)
    var best = if (loudest) 0.0 else Double.MAX_VALUE
    var start = 0
    while (start < samples) {
        val end = minOf(start + win, samples)
        var sum = 0.0
        for (i in start until end) {
            val v = ((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xFF)).toShort().toInt()
            sum += v.toDouble() * v
        }
        val mean = sum / (end - start)
        best = if (loudest) maxOf(best, mean) else minOf(best, mean)
        start += win
    }
    return (kotlin.math.sqrt(best) / 5_000.0).toFloat().coerceIn(0f, 1f)
}

/**
 * Decides whether a sound heard during "Hey Seema" standby goes online (Deepgram) to check for her
 * name. The bar follows the room: max(110 RMS, room + 35 RMS), where the room is the quiet between
 * words in recent clips. A fan or TV raises the bar a little instead of pausing checks: the earlier
 * pause after repeated noise ("online checks paused") also dropped a real "Hey Seema" said during it.
 */
class WakeCheckGate(private val minLevel: Float = WAKE_ENERGY_THRESHOLD) {
    enum class Decision { CHECK, TOO_QUIET }

    /** The room's level, learnt from recent clips (0 until the first clip). */
    var room = 0f
        private set

    fun decide(pcm: ByteArray): Decision {
        if (pcm.size < 2) return Decision.TOO_QUIET
        val bar = maxOf(minLevel, room + WAKE_ABOVE_ROOM)
        val decision = if (loudestLevel(pcm) < bar) Decision.TOO_QUIET else Decision.CHECK
        room += (quietestLevel(pcm) - room) * WAKE_ROOM_FOLLOW
        return decision
    }
}
