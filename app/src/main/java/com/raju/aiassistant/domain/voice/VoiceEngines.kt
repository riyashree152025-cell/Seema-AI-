package com.raju.aiassistant.domain.voice

import com.raju.aiassistant.domain.model.TtsEngine

/** A model archive that is downloaded once, checked against its SHA-256 and unpacked on the phone ("Hey Seema"). */
interface DownloadableModel {
    val id: String
    val title: String
    val url: String
    val sizeBytes: Long
    val sha256: String
    /** Top-level directory inside the archive. */
    val dirName: String
    /** A file inside [dirName] that must exist after unpacking. */
    val modelFile: String
    /** Increase when the catalog points to a new archive: installed copies then show "Update". */
    val version: Int
}

/** Measured behaviour of one voice engine (updated after every sentence). */
data class EngineStats(
    /** Moving average of synthesis time ÷ audio length (below 1 = faster than speech). */
    val rtf: Double? = null,
    /** Moving average of the time to synthesise one sentence, in ms (≈ first-audio delay). */
    val firstAudioMs: Long? = null,
    val successes: Int = 0,
    val failures: Int = 0,
    /** Failures in a row (reset by a success). */
    val recentFailures: Int = 0,
    val lastError: String? = null,
) {
    fun withSuccess(rtfNow: Double, synthMs: Long) = copy(
        rtf = rtf?.let { it * 0.7 + rtfNow * 0.3 } ?: rtfNow,
        firstAudioMs = firstAudioMs?.let { (it * 0.7 + synthMs * 0.3).toLong() } ?: synthMs,
        successes = successes + 1,
        recentFailures = 0,
    )

    fun withFailure(error: String) = copy(failures = failures + 1, recentFailures = recentFailures + 1, lastError = error.take(160))
}

/** A voice that failed this many times in a row (credits used up, rate limit, error) is tried after the others. */
const val MAX_RECENT_FAILURES = 2

/**
 * The order in which Seema's three voices are tried for a sentence; the next one takes over at once when one
 * fails. Automatic: Sarvam, then Cartesia (a voice that keeps failing goes behind the other), Edge Neural last —
 * free, no key, so she never runs out of voice. Manual: the chosen voice first, then the others the same way.
 */
fun voiceEngineOrder(automatic: Boolean, chosen: TtsEngine, stats: Map<TtsEngine, EngineStats>): List<TtsEngine> {
    fun healthy(e: TtsEngine) = (stats[e]?.recentFailures ?: 0) < MAX_RECENT_FAILURES
    val paid = listOf(TtsEngine.SARVAM, TtsEngine.CARTESIA).sortedBy { if (healthy(it)) 0 else 1 }
    if (automatic) return paid + TtsEngine.EDGE
    return (listOf(chosen) + paid + TtsEngine.EDGE).distinct()
}
