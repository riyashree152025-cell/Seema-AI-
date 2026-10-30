package com.raju.aiassistant.domain.voice

import com.raju.aiassistant.domain.model.TtsEngine

/** How an offline voice model is run by the sherpa-onnx runtime. */
enum class LocalModelKind { PIPER_VITS, KOKORO }

/** One selectable voice of a model (Kokoro has many speakers in one model). */
data class LocalSpeaker(val id: Int, val name: String, val note: String)

/**
 * An offline voice model that can be downloaded once and then runs on the phone without
 * internet. Every field was checked against the real release archive (size, SHA-256, files,
 * speakers, license); see docs/OFFLINE_VOICE_ENGINES.md. New engines are added here.
 */
data class LocalVoiceModel(
    val id: String,
    val engine: TtsEngine,
    val kind: LocalModelKind,
    val title: String,
    val language: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    /** Top-level directory inside the archive. */
    val dirName: String,
    /** Model file inside [dirName]. */
    val modelFile: String,
    val voicesFile: String = "",
    val speakers: List<LocalSpeaker>,
    val license: String,
    /** Increase when the catalog points to a new archive: installed copies then show "Update". */
    val version: Int = 1,
    /** espeak-ng language for Kokoro. */
    val lang: String = "",
)

private const val TTS_MODELS = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

/** The verified offline voices (Hindi female voices first). */
val LOCAL_VOICE_CATALOG = listOf(
    LocalVoiceModel(
        id = "piper-hi-priyamvada-int8",
        engine = TtsEngine.PIPER,
        kind = LocalModelKind.PIPER_VITS,
        title = "Piper · Priyamvada (Hindi)",
        language = "hi-IN",
        url = "$TTS_MODELS/vits-piper-hi_IN-priyamvada-medium-int8.tar.bz2",
        sizeBytes = 21_097_319,
        sha256 = "2997c7bbc211e524af83dfdffe7712942f10bd76c079a526af2e09d1f36a92f2",
        dirName = "vits-piper-hi_IN-priyamvada-medium-int8",
        modelFile = "hi_IN-priyamvada-medium.onnx",
        speakers = listOf(LocalSpeaker(0, "Priyamvada", "Hindi, female voice (measured pitch ≈ 220 Hz)")),
        license = "CC BY-NC-SA 4.0 — personal / non-commercial use",
    ),
    LocalVoiceModel(
        id = "kokoro-multi-lang-v1_0-int8",
        engine = TtsEngine.KOKORO,
        kind = LocalModelKind.KOKORO,
        title = "Kokoro-82M v1.0 (multi-language, int8)",
        language = "hi-IN",
        url = "$TTS_MODELS/kokoro-int8-multi-lang-v1_0.tar.bz2",
        sizeBytes = 132_353_410,
        sha256 = "72c347230107ef8a31098ec4d468a605244beef5f1ed00db97efa13518800653",
        dirName = "kokoro-int8-multi-lang-v1_0",
        modelFile = "model.int8.onnx",
        voicesFile = "voices.bin",
        speakers = listOf(
            LocalSpeaker(31, "hf_alpha", "Hindi, female (≈ 220 Hz)"),
            LocalSpeaker(32, "hf_beta", "Hindi, female, a little lower (≈ 190 Hz)"),
            LocalSpeaker(33, "hm_omega", "Hindi, male"),
            LocalSpeaker(34, "hm_psi", "Hindi, male"),
        ),
        license = "Apache-2.0",
        lang = "hi",
    ),
)

/** Measured behaviour of one voice engine on this phone (updated after every sentence). */
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

/** Kokoro is used automatically only while it is clearly faster than speech; otherwise Piper takes over. */
const val KOKORO_MAX_RTF = 0.8
/** An engine that failed this many times in a row is skipped in Automatic mode until it works again. */
const val MAX_RECENT_FAILURES = 2

/**
 * The order in which voice engines are tried.
 *
 * Automatic: once an offline model is installed, only engines on the phone are used (no voice API
 * costs): Kokoro first when it is fast enough on this phone, Piper next, Android TTS last. An engine
 * that keeps failing is moved behind the others. Until a model is downloaded, the chosen engine is
 * kept so nothing changes for the user. Manual: the chosen engine first, then the offline engines,
 * then the other (cloud) engines as before, Android TTS last.
 */
fun voiceEngineOrder(
    automatic: Boolean,
    chosen: TtsEngine,
    installed: Set<TtsEngine>,
    stats: Map<TtsEngine, EngineStats>,
): List<TtsEngine> {
    fun healthy(e: TtsEngine) = (stats[e]?.recentFailures ?: 0) < MAX_RECENT_FAILURES
    val kokoroFast = stats[TtsEngine.KOKORO]?.rtf?.let { it <= KOKORO_MAX_RTF } ?: true // unmeasured: try it once
    val local = buildList {
        if (TtsEngine.KOKORO in installed && kokoroFast) add(TtsEngine.KOKORO)
        if (TtsEngine.PIPER in installed) add(TtsEngine.PIPER)
        if (TtsEngine.KOKORO in installed && !kokoroFast) add(TtsEngine.KOKORO)
    }.sortedBy { if (healthy(it)) 0 else 1 }
    if (automatic && local.isNotEmpty()) return local + TtsEngine.ANDROID
    // Chosen engine, then the offline voices, then the other engines as before; Android TTS always last.
    val rest = providerOrder(chosen).filter { it != TtsEngine.ANDROID && it !in local && !(it.isOffline && it !in installed) }
    return (listOf(chosen) + local + rest + TtsEngine.ANDROID).distinct()
}

val TtsEngine.isOffline: Boolean get() = this == TtsEngine.PIPER || this == TtsEngine.KOKORO || this == TtsEngine.ANDROID
