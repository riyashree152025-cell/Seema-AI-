package com.raju.aiassistant.domain.voice

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Sleep mode's ear: decides from the loudness (RMS) of 20 ms frames when someone starts talking,
 * when they stop, and whether what was recorded sounds like a voice at all. Pure logic, so it is
 * unit-tested without a microphone.
 *
 * What went wrong on the phone (logcat, 6 Nov 2026), and what this does about it:
 * - With an AC running the room noise moves up and down. The old pause test compared with the
 *   quietest noise level, so the AC itself counted as "still talking": recordings ran to the 7.5 s
 *   limit, went to the cloud, came back empty, and a real "सीमा" said meanwhile was missed.
 *   Now the noise level follows a steady sound within a few seconds, and a pause is measured
 *   against the loudest part of this utterance as well (a voice drops far below its own peak).
 * - Steady sound with no syllable rhythm (AC, fan, traffic hum) is dropped on the phone instead of
 *   being sent to speech recognition.
 */
class WakeEndpointer(
    private val frameMs: Int = 20,
    private val minSpeech: Double = 110.0,
    private val startFrames: Int = 4,
    private val endSilenceMs: Int = 600,
    private val maxMs: Int = 8_000,
    private val minSpeechMs: Int = 200,
) {
    enum class Event { WAITING, STARTED, RECORDING, DONE, DISCARDED }

    /** Room noise level (follows a steady sound up within a few seconds, falls quickly). */
    var noise = -1.0
        private set
    private var loudRun = 0
    private var inUtterance = false
    private var totalMs = 0
    private var quietMs = 0
    private var speechMs = 0
    private var peak = 0.0
    private val recent = ArrayDeque<Double>()
    private val levels = ArrayList<Double>()

    /** Length of the utterance so far (or of the last one). */
    val utteranceMs: Int get() = totalMs

    fun onFrame(level: Double): Event {
        if (!inUtterance) {
            noise = when {
                noise < 0 -> level
                level < noise -> noise * 0.95 + level * 0.05
                else -> noise * 0.995 + level * 0.005
            }
            loudRun = if (level > maxOf(noise * 2.2, minSpeech)) loudRun + 1 else 0
            if (loudRun < startFrames) return Event.WAITING
            inUtterance = true
            totalMs = 0
            quietMs = 0
            speechMs = loudRun * frameMs
            peak = level
            recent.clear()
            levels.clear()
            loudRun = 0
            return Event.STARTED
        }
        totalMs += frameMs
        levels += level
        recent.addLast(level)
        if (recent.size > 5) recent.removeFirst()
        val smooth = recent.average()
        if (smooth > peak) peak = smooth
        val quietBar = maxOf(noise * 1.8, minSpeech, peak * 0.2)
        if (smooth < quietBar) quietMs += frameMs else { quietMs = 0; speechMs += frameMs }
        if (quietMs < endSilenceMs && totalMs < maxMs) return Event.RECORDING
        inUtterance = false
        val voiced = levels.dropLast(quietMs / frameMs)
        if (speechMs < minSpeechMs || !soundsLikeVoice(voiced)) {
            // A steady sound (or a knock): learn it as the room's noise and keep waiting.
            if (voiced.isNotEmpty()) noise = maxOf(noise, voiced.sorted()[voiced.size / 2] * 0.9)
            return Event.DISCARDED
        }
        return Event.DONE
    }

    companion object {
        /**
         * A voice goes up and down with its syllables (frame levels vary a lot); an AC or a fan is
         * nearly flat. Coefficient of variation of the frame levels: speech is well above 0.35.
         */
        fun soundsLikeVoice(levels: List<Double>): Boolean {
            if (levels.size < 10) return true
            val mean = levels.average()
            if (mean <= 0.0) return false
            val sd = sqrt(levels.sumOf { (it - mean) * (it - mean) } / levels.size)
            return sd / mean >= 0.35
        }
    }
}

/**
 * Makes quiet 16-bit PCM louder (at most [maxGain] times) so its peaks reach about 60 % of full
 * scale. Phones gave speech at RMS 120-400 of 32767 — very quiet for a cloud recogniser, which then
 * often returned no words. Loud recordings are left unchanged; nothing is clipped.
 */
fun normalizePcm16(pcm: ByteArray, maxGain: Double = 8.0, targetPeak: Double = 0.6 * 32767): ByteArray {
    val n = pcm.size / 2
    if (n == 0) return pcm
    // The 99.9th-percentile sample, so one click does not decide the gain.
    val mags = IntArray(n) { i -> abs(((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xFF)).toShort().toInt()) }
    val sorted = mags.sortedArray()
    val ref = sorted[minOf(n - 1, (n * 0.999).toInt())].coerceAtLeast(1)
    val gain = minOf(maxGain, targetPeak / ref)
    if (gain <= 1.2) return pcm
    val out = ByteArray(n * 2)
    for (i in 0 until n) {
        val s = ((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xFF)).toShort().toInt()
        val v = (s * gain).toInt().coerceIn(-32768, 32767)
        out[2 * i] = (v and 0xFF).toByte()
        out[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
    }
    return out
}

/**
 * True when a transcript is mostly in a script the user does not speak (Telugu, Hebrew …): the
 * recogniser guessed the wrong language from noise or a short clip. Hindi (Devanagari), Latin and,
 * when [bengali], Bengali letters are expected.
 */
fun isUnexpectedScript(text: String, bengali: Boolean = false): Boolean {
    var letters = 0
    var foreign = 0
    for (ch in text) {
        if (!ch.isLetter()) continue
        letters++
        val ok = ch in 'a'..'z' || ch in 'A'..'Z' || ch in 'À'..'ɏ' || ch in 'ऀ'..'ॿ' ||
            (bengali && ch in 'ঀ'..'৿')
        if (!ok) foreign++
    }
    return letters > 0 && foreign * 10 >= letters * 3
}
